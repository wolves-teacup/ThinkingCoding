package com.thinkingcoding.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkingcoding.config.AppConfig;
import com.thinkingcoding.model.ChatMessage;
import com.thinkingcoding.model.ToolCall;
import com.thinkingcoding.tools.BaseTool;
import com.thinkingcoding.tools.ToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 集成LangChain4j和DeepSeek API的AI服务实现。
 *
 * 完全使用 LangChain4j 原生 Tool Calling：
 * - 请求阶段注入 ToolSpecification
 * - 响应阶段读取 ToolExecutionRequest
 * - 不再依赖正则解析文本命令
 */
public class LangChainService implements AIService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AppConfig appConfig;
    private final ToolRegistry toolRegistry;
    private final ContextManager contextManager;
    private Consumer<ChatMessage> messageHandler;
    private Consumer<ToolCall> toolCallHandler;
    private StreamingChatModel streamingChatModel;

    private volatile boolean isGenerating = false;
    private volatile boolean shouldStop = false;

    public LangChainService(AppConfig appConfig, ToolRegistry toolRegistry, ContextManager contextManager) {
        this.appConfig = appConfig;
        this.toolRegistry = toolRegistry;
        this.contextManager = contextManager;
        initializeChatModel();
    }

    private void initializeChatModel() {
        try {
            AppConfig.ModelConfig modelConfig = appConfig.getModelConfig(appConfig.getDefaultModel());
            if (modelConfig != null) {
                this.streamingChatModel = createDeepSeekModel(modelConfig);
            }
        } catch (Exception e) {
            System.err.println("初始化模型失败: " + e.getMessage());
        }
    }

    private StreamingChatModel createDeepSeekModel(AppConfig.ModelConfig config) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(config.getBaseURL())
                .apiKey(config.getApiKey())
                .modelName(config.getName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Override
    public List<ChatMessage> chat(String input, List<ChatMessage> history, String modelName) {
        throw new UnsupportedOperationException("Use streamingChat for real AI service");
    }

    @Override
    public List<ChatMessage> streamingChat(String input, List<ChatMessage> history, String modelName) {
        // 前置校验：确保消息处理器和流式模型已初始化
        if (messageHandler == null) {
            throw new IllegalStateException("Message handler not set");
        }

        if (streamingChatModel == null) {
            throw new IllegalStateException("DeepSeek model not initialized. Please check your configuration.");
        }

        // 初始化生成状态标志
        isGenerating = true;
        shouldStop = false;

        final StringBuilder fullResponse = new StringBuilder();
        final CompletableFuture<Void> completionFuture = new CompletableFuture<>();

        try {
            // 准备对话消息和工具规格定义
            List<dev.langchain4j.data.message.ChatMessage> messages = prepareMessages(input, history);
            List<ToolSpecification> toolSpecifications = buildToolSpecifications();

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecifications)
                    .build();

            // 执行流式对话请求，注册响应处理器
            streamingChatModel.chat(request, new StreamingChatResponseHandler() {
                /**
                 * 处理AI响应的部分片段（Token级别）
                 */
                @Override
                public void onPartialResponse(String token) {
                    if (shouldStop) {
                        return;
                    }
                    fullResponse.append(token);
                    messageHandler.accept(new ChatMessage("assistant", token));
                }

                /**
                 * 处理AI响应完成事件，提取完整文本和工具调用
                 */
                @Override
                public void onCompleteResponse(ChatResponse chatResponse) {
                    try {
                        // 如果用户主动停止生成，添加截断标记
                        if (shouldStop && !fullResponse.isEmpty()) {
                            ChatMessage truncatedMessage = new ChatMessage("assistant",
                                    fullResponse + "\n\n💡 [生成已被用户停止]");
                            history.add(truncatedMessage);
                            return;
                        }

                        // 将完整的AI响应添加到历史记录
                        String assistantText = fullResponse.toString().trim();
                        if (!assistantText.isEmpty()) {
                            history.add(new ChatMessage("assistant", assistantText));
                        }

                        // 提取并处理工具调用请求
                        List<ToolCall> toolCalls = extractToolCalls(chatResponse);
                        if (toolCallHandler != null && !toolCalls.isEmpty()) {
                            // 当前 AgentLoop 只缓存一个待执行调用，这里按顺序触发并由上层取最后一个。
                            for (ToolCall toolCall : toolCalls) {
                                toolCallHandler.accept(toolCall);
                            }
                        }

                        System.out.println();
                    } finally {
                        // 重置生成状态并完成Future
                        isGenerating = false;
                        shouldStop = false;
                        completionFuture.complete(null);
                    }
                }

                /**
                 * 处理API调用错误，提供友好的错误提示
                 */
                @Override
                public void onError(Throwable error) {
                    try {
                        System.err.println("❌ DeepSeek API error: " + error.getMessage());
                        ChatMessage errorMessage = new ChatMessage("assistant",
                                "抱歉，我在处理您的请求时遇到了问题： " + error.getMessage());
                        messageHandler.accept(errorMessage);
                        history.add(errorMessage);
                    } finally {
                        // 重置生成状态并异常完成Future
                        isGenerating = false;
                        shouldStop = false;
                        completionFuture.completeExceptionally(error);
                    }
                }
            });

            // 等待流式响应完成，设置5分钟超时
            try {
                completionFuture.get(5, TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                System.err.println("⚠️  流式响应超时");
                completionFuture.cancel(true);
            } catch (Exception e) {
                System.err.println("⚠️  等待流式响应时发生错误: " + e.getMessage());
            }

        } catch (Exception e) {
            // 捕获服务层异常，重置状态并提供错误反馈
            isGenerating = false;
            shouldStop = false;

            System.err.println("❌ Service error: " + e.getMessage());
            ChatMessage errorMessage = new ChatMessage("assistant",
                    "服务暂时不可用，请稍后重试。错误信息: " + e.getMessage());
            messageHandler.accept(errorMessage);
            history.add(errorMessage);
        }

        return history;
    }

    private List<ToolCall> extractToolCalls(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.aiMessage() == null || !chatResponse.aiMessage().hasToolExecutionRequests()) {
            return Collections.emptyList();
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        for (ToolExecutionRequest request : chatResponse.aiMessage().toolExecutionRequests()) {
            Map<String, Object> arguments = parseToolArguments(request.arguments());
            toolCalls.add(normalizeToolCall(request.name(), arguments));
        }
        return toolCalls;
    }

    private ToolCall normalizeToolCall(String requestToolName, Map<String, Object> rawArguments) {
        String toolName = requestToolName;
        Map<String, Object> arguments = new HashMap<>(rawArguments);

        if ("file_manager".equals(requestToolName)) {
            String command = getString(arguments, "command");
            if ("write".equalsIgnoreCase(command)) {
                toolName = "write_file";
                arguments.remove("command");
            } else if ("read".equalsIgnoreCase(command)) {
                toolName = "read_file";
                arguments.remove("command");
            } else if ("list".equalsIgnoreCase(command)) {
                toolName = "list_directory";
                arguments.remove("command");
            }
        } else if ("command_executor".equals(requestToolName) && !arguments.containsKey("command") && arguments.containsKey("input")) {
            arguments.put("command", String.valueOf(arguments.get("input")));
        }

        return new ToolCall(toolName, arguments, null, false, 0, true);
    }

    private String getString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> parseToolArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new HashMap<>();
        }

        try {
            return OBJECT_MAPPER.readValue(arguments, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("input", arguments);
            return fallback;
        }
    }

    private List<ToolSpecification> buildToolSpecifications() {
        List<ToolSpecification> specifications = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (BaseTool tool : toolRegistry.getAllTools()) {
            ToolSpecification specification = ToolSpecification.builder()
                    .name(tool.getName())
                    .description(tool.getDescription())
                    .parameters(resolveToolSchema(tool))
                    .build();
            specifications.add(specification);
            seen.add(tool.getName());
        }

        // 兼容 CLI 既有展示逻辑，暴露语义化别名工具。
        // 🔥 修复：添加前检查是否已存在同名工具，避免重复
        if (seen.contains("file_manager")) {
            addAliasIfNotExists(specifications, seen, "write_file", 
                    buildWriteFileAliasSpecification());
            addAliasIfNotExists(specifications, seen, "read_file", 
                    buildReadFileAliasSpecification());
            addAliasIfNotExists(specifications, seen, "list_directory", 
                    buildListDirectoryAliasSpecification());
        }
        if (seen.contains("command_executor")) {
            addAliasIfNotExists(specifications, seen, "bash", 
                    buildBashAliasSpecification());
        }

        return specifications;
    }

    /**
     * 🔥 新增：仅在工具名称不存在时添加别名工具
     */
    private void addAliasIfNotExists(List<ToolSpecification> specifications, 
                                     Set<String> seen, 
                                     String aliasName, 
                                     ToolSpecification aliasSpec) {
        if (!seen.contains(aliasName)) {
            specifications.add(aliasSpec);
            seen.add(aliasName);
        }
        // 静默跳过，避免重复
    }

    private ToolSpecification buildWriteFileAliasSpecification() {
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .addStringProperty("path", "目标文件路径")
                .addStringProperty("content", "要写入的完整内容")
                .required("path", "content")
                .additionalProperties(false)
                .build();

        return ToolSpecification.builder()
                .name("write_file")
                .description("写入或覆盖文件内容")
                .parameters(parameters)
                .build();
    }

    private ToolSpecification buildReadFileAliasSpecification() {
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .addStringProperty("path", "要读取的文件路径")
                .required("path")
                .additionalProperties(false)
                .build();

        return ToolSpecification.builder()
                .name("read_file")
                .description("读取文件内容")
                .parameters(parameters)
                .build();
    }

    private ToolSpecification buildListDirectoryAliasSpecification() {
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .addStringProperty("path", "要列出的目录路径")
                .required("path")
                .additionalProperties(false)
                .build();

        return ToolSpecification.builder()
                .name("list_directory")
                .description("列出目录中的文件和子目录")
                .parameters(parameters)
                .build();
    }

    private ToolSpecification buildBashAliasSpecification() {
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .addStringProperty("command", "要执行的系统命令")
                .required("command")
                .additionalProperties(false)
                .build();

        return ToolSpecification.builder()
                .name("bash")
                .description("执行 shell 命令")
                .parameters(parameters)
                .build();
    }

    private JsonObjectSchema resolveToolSchema(BaseTool tool) {
        Object inputSchema = tool.getInputSchema();
        if (inputSchema != null) {
            try {
                Map<String, Object> schemaMap = OBJECT_MAPPER.convertValue(inputSchema, new TypeReference<Map<String, Object>>() {
                });
                return toJsonObjectSchema(schemaMap);
            } catch (Exception ignored) {
                // 降级到内置 schema
            }
        }
        return defaultToolSchema(tool.getName());
    }

    private JsonObjectSchema defaultToolSchema(String toolName) {
        if ("file_manager".equals(toolName)) {
            return JsonObjectSchema.builder()
                    .addEnumProperty("command", Arrays.asList("read", "write", "list", "create", "delete", "info"), "文件操作类型")
                    .addStringProperty("path", "文件或目录路径")
                    .addStringProperty("content", "当 command=write 时需要")
                    .required("command", "path")
                    .additionalProperties(false)
                    .build();
        }

        if ("command_executor".equals(toolName)) {
            return JsonObjectSchema.builder()
                    .addStringProperty("command", "要执行的命令")
                    .required("command")
                    .additionalProperties(false)
                    .build();
        }

        if ("grep_search".equals(toolName)) {
            return JsonObjectSchema.builder()
                    .addStringProperty("query", "检索关键词或正则表达式")
                    .addStringProperty("includePattern", "文件匹配规则，如 *.java")
                    .addBooleanProperty("isRegexp", "是否启用正则")
                    .required("query")
                    .additionalProperties(false)
                    .build();
        }

        return JsonObjectSchema.builder().additionalProperties(true).build();
    }

    private JsonObjectSchema toJsonObjectSchema(Map<String, Object> schemaMap) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();

        Object description = schemaMap.get("description");
        if (description != null) {
            builder.description(String.valueOf(description));
        }

        Object properties = schemaMap.get("properties");
        if (properties instanceof Map<?, ?> propsMap) {
            for (Map.Entry<?, ?> entry : propsMap.entrySet()) {
                String name = String.valueOf(entry.getKey());
                JsonSchemaElement element = toJsonSchemaElement(entry.getValue());
                builder.addProperty(name, element);
            }
        }

        Object required = schemaMap.get("required");
        if (required instanceof List<?> requiredList) {
            builder.required(requiredList.stream().map(String::valueOf).toArray(String[]::new));
        } else if (required instanceof String[] requiredArray) {
            builder.required(requiredArray);
        }

        Object additionalProperties = schemaMap.get("additionalProperties");
        if (additionalProperties instanceof Boolean flag) {
            builder.additionalProperties(flag);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private JsonSchemaElement toJsonSchemaElement(Object rawDefinition) {
        if (!(rawDefinition instanceof Map<?, ?> map)) {
            return JsonStringSchema.builder().build();
        }

        Object typeValue = map.containsKey("type") ? map.get("type") : "string";
        String type = String.valueOf(typeValue).toLowerCase(Locale.ROOT);
        String description = map.containsKey("description") ? String.valueOf(map.get("description")) : null;

        Object enumValues = map.get("enum");
        if (enumValues instanceof List<?> values && !values.isEmpty()) {
            JsonEnumSchema.Builder enumBuilder = JsonEnumSchema.builder()
                    .enumValues(values.stream().map(String::valueOf).toArray(String[]::new));
            if (description != null) {
                enumBuilder.description(description);
            }
            return enumBuilder.build();
        }

        return switch (type) {
            case "boolean" -> {
                JsonBooleanSchema.Builder booleanBuilder = JsonBooleanSchema.builder();
                if (description != null) {
                    booleanBuilder.description(description);
                }
                yield booleanBuilder.build();
            }
            case "integer" -> {
                JsonIntegerSchema.Builder integerBuilder = JsonIntegerSchema.builder();
                if (description != null) {
                    integerBuilder.description(description);
                }
                yield integerBuilder.build();
            }
            case "number" -> {
                JsonNumberSchema.Builder numberBuilder = JsonNumberSchema.builder();
                if (description != null) {
                    numberBuilder.description(description);
                }
                yield numberBuilder.build();
            }
            case "array" -> {
                JsonArraySchema.Builder arrayBuilder = JsonArraySchema.builder();
                if (description != null) {
                    arrayBuilder.description(description);
                }
                Object items = map.get("items");
                arrayBuilder.items(toJsonSchemaElement(items));
                yield arrayBuilder.build();
            }
            case "object" -> {
                Map<String, Object> nested = new HashMap<>((Map<String, Object>) map);
                yield toJsonObjectSchema(nested);
            }
            default -> {
                JsonStringSchema.Builder stringBuilder = JsonStringSchema.builder();
                if (description != null) {
                    stringBuilder.description(description);
                }
                yield stringBuilder.build();
            }
        };
    }

    private List<dev.langchain4j.data.message.ChatMessage>  prepareMessages(String input, List<ChatMessage> history) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        if (contextManager != null) {
            ChatMessage projectContext = contextManager.buildProjectContextMessage();
            if (projectContext != null) {
                messages.add(dev.langchain4j.data.message.SystemMessage.from(projectContext.getContent()));
            }
        }

        List<ChatMessage> managedHistory = history;
        if (contextManager != null && history != null && !history.isEmpty()) {
            managedHistory = contextManager.getContextForAI(history);
        }

        if (managedHistory != null && !managedHistory.isEmpty()) {
            messages.addAll(convertToLangChainHistory(managedHistory));
        }

        messages.add(dev.langchain4j.data.message.UserMessage.from(input));
        return messages;
    }

    private List<dev.langchain4j.data.message.ChatMessage> convertToLangChainHistory(List<ChatMessage> history) {
        return history.stream()
                .map(msg -> {
                    if ("user".equals(msg.getRole())) {
                        return dev.langchain4j.data.message.UserMessage.from(msg.getContent());
                    } else if ("assistant".equals(msg.getRole())) {
                        return dev.langchain4j.data.message.AiMessage.from(msg.getContent());
                    } else {
                        return dev.langchain4j.data.message.SystemMessage.from(msg.getContent());
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public void setMessageHandler(Consumer<ChatMessage> handler) {
        this.messageHandler = handler;
    }

    @Override
    public void setToolCallHandler(Consumer<ToolCall> handler) {
        this.toolCallHandler = handler;
    }

    @Override
    public boolean validateModel(String modelName) {
        return appConfig.getModels().containsKey(modelName);
    }

    @Override
    public List<String> getAvailableModels() {
        return new ArrayList<>(appConfig.getModels().keySet());
    }


    public boolean isGenerating() {
        return isGenerating;
    }

    public void stopCurrentGeneration() {
        if (isGenerating) {
            shouldStop = true;
            System.out.println("⏸️  正在停止生成...");
        }
    }
}

