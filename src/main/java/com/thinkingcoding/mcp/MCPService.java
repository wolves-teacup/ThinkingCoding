package com.thinkingcoding.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkingcoding.mcp.model.MCPTool;
import com.thinkingcoding.model.ToolResult;
import com.thinkingcoding.tools.BaseTool; // 使用你的 BaseTool 基类
import com.thinkingcoding.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP服务，管理与多个MCP服务器的连接和工具调用
 */
public class MCPService {
    private static final Logger log = LoggerFactory.getLogger(MCPService.class);
    private final Map<String, MCPClient> connectedServers = new ConcurrentHashMap<>();
    private final Map<String, BaseTool> mcpTools = new ConcurrentHashMap<>(); // 改为 BaseTool
    private final ToolRegistry toolRegistry;

    public MCPService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
    // 添加 clients 映射
    private final Map<String, MCPClient> clients = new ConcurrentHashMap<>();


    // 🔥 新增3参数方法
    public List<BaseTool> connectToServer(String serverName, String command, List<String> args) {
        try {
            log.debug("启动MCP服务器: {} - {}", serverName, command);
            log.debug("参数: {}", args);

            // 清理旧连接
            if (clients.containsKey(serverName)) {
                MCPClient existingClient = clients.get(serverName);
                if (existingClient != null && existingClient.isConnected()) {
                    existingClient.disconnect();
                }
                clients.remove(serverName);
                connectedServers.remove(serverName); // 🔥 同时清理 connectedServers
            }

            MCPClient client = new MCPClient(serverName);
            boolean connected = client.connect(command, args);

            if (connected) {
                // 🔥 保存到两个映射中
                clients.put(serverName, client);
                connectedServers.put(serverName, client);

                List<MCPTool> mcpToolList = client.getAvailableTools();
                List<BaseTool> baseTools = convertToBaseTools(mcpToolList, serverName);

                // 🔥 保存工具到 mcpTools 映射
                for (int i = 0; i < mcpToolList.size(); i++) {
                    String toolKey = mcpToolList.get(i).getName(); // 使用工具名称作为key
                    mcpTools.put(toolKey, baseTools.get(i));
                }

                log.debug("✅ 成功连接MCP服务器: {} ({} 个工具)", serverName, baseTools.size());
                return baseTools;
            } else {
                log.debug("⚠️ 连接MCP服务器失败: {}", serverName);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("❌ 连接MCP服务器异常: {}", serverName, e);
            return Collections.emptyList();
        }
    }

    private List<BaseTool> convertToBaseTools(List<MCPTool> mcpTools, String serverName) {
        List<BaseTool> baseTools = new ArrayList<>();
        for (MCPTool mcpTool : mcpTools) {
            BaseTool baseTool = new BaseTool(mcpTool.getName(), mcpTool.getDescription()) {
                @Override
                public ToolResult execute(String input) {
                    try {
                        // 🔥 修复：正确解析JSON参数
                        Map<String, Object> parameters = parseInputToParameters(input);
                        
                        // 🔥 增强：记录工具调用信息
                        log.info("🔧 执行MCP工具 [{}]: {} - 参数: {}", serverName, mcpTool.getName(), parameters);
                        
                        Object result = callTool(serverName, mcpTool.getName(), parameters);
                        
                        log.info("✅ MCP工具执行成功: {}", mcpTool.getName());
                        return success(result != null ? result.toString() : "执行成功");
                    } catch (Exception e) {
                        // 🔥 增强：提供详细的错误信息和排查建议
                        String errorMsg = buildDetailedErrorMessage(serverName, mcpTool.getName(), e);
                        log.error("❌ {}", errorMsg);
                        return error(errorMsg);
                    }
                }

                @Override
                public String getCategory() {
                    return "MCP-" + serverName;
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }

                // 🔥 关键修复：暴露inputSchema给系统提示词（重写BaseTool方法）
                public Object getInputSchema() {
                    return mcpTool.getInputSchema();
                }
            };
            baseTools.add(baseTool);
        }
        return baseTools;
    }

    /**
     * 🔥 新增：构建详细的错误消息，包含排查建议
     */
    private String buildDetailedErrorMessage(String serverName, String toolName, Exception e) {
        StringBuilder errorMsg = new StringBuilder();
        errorMsg.append("MCP工具执行失败 [").append(serverName).append("/").append(toolName).append("]: ");
        errorMsg.append(getRootMessage(e));
        
        // 🔥 根据错误类型提供排查建议
        String errorMessage = getRootMessage(e).toLowerCase(Locale.ROOT);
        
        if (errorMessage.contains("fetch failed")) {
            errorMsg.append("\n\n💡 可能原因和解决方案：");
            errorMsg.append("\n1. GitHub Token 未配置或无效");
            errorMsg.append("\n   - 检查 MCP 服务器启动参数是否包含 --token");
            errorMsg.append("\n   - 确认 Token 有正确的权限（repo, user 等）");
            errorMsg.append("\n2. 网络连接问题");
            errorMsg.append("\n   - 检查是否可以访问 https://api.github.com");
            errorMsg.append("\n   - 如有代理，请确认代理设置正确");
            errorMsg.append("\n3. 参数格式错误");
            errorMsg.append("\n   - 检查是否提供了所有必需参数");
            errorMsg.append("\n   - 确认参数名称和类型正确");
        } else if (errorMessage.contains("token") || errorMessage.contains("auth")) {
            errorMsg.append("\n\n💡 认证失败，请检查：");
            errorMsg.append("\n- GitHub Token 是否正确配置");
            errorMsg.append("\n- Token 是否过期或被撤销");
            errorMsg.append("\n- Token 是否有足够的权限");
        } else if (errorMessage.contains("timeout") || errorMessage.contains("超时")) {
            errorMsg.append("\n\n💡 请求超时，请检查：");
            errorMsg.append("\n- 网络连接是否正常");
            errorMsg.append("\n- GitHub API 是否可访问");
            errorMsg.append("\n- 是否需要配置代理");
        }
        
        return errorMsg.toString();
    }

    /**
     * 🔥 修复：优先解析JSON格式的参数
     * 如果输入是JSON对象，直接解析为Map；否则作为单个参数
     */
    private Map<String, Object> parseInputToParameters(String input) {
        Map<String, Object> parameters = new HashMap<>();

        if (input == null || input.trim().isEmpty()) {
            return parameters;
        }

        // 🔥 优先尝试解析JSON
        if (input.trim().startsWith("{")) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> parsed = mapper.readValue(input, Map.class);
                log.debug("✅ 成功解析JSON参数: {}", parsed);
                return parsed;
            } catch (Exception e) {
                log.debug("⚠️ JSON解析失败，使用默认解析: {}", e.getMessage());
            }
        }

        // 如果不是JSON或解析失败，将整个输入作为单个参数
        parameters.put("input", input);
        return parameters;
    }

    public Object callTool(String serverName, String toolName, Map<String, Object> arguments) {
        try {
            MCPClient client = clients.get(serverName);
            if (client == null) {
                throw new IllegalStateException("MCP服务器未连接: " + serverName);
            }
            return client.callTool(toolName, arguments);
        } catch (Exception e) {
            log.error("调用工具失败: {}.{}", serverName, toolName, e);
            throw new RuntimeException("工具调用失败: " + getRootMessage(e), e);
        }
    }

    private String getRootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.toString();
    }





    private List<BaseTool> convertTools(List<MCPTool> mcpTools, MCPClient client) {
        List<BaseTool> result = new ArrayList<>();
        for (MCPTool mcpTool : mcpTools) {
            result.add(new MCPToolAdapter(mcpTool, client));
        }
        return result;
    }

    public void disconnectServer(String serverName) {
        MCPClient client = connectedServers.remove(serverName);
        if (client != null) {
            // 移除相关工具
            mcpTools.entrySet().removeIf(entry -> {
                boolean shouldRemove = entry.getKey().startsWith("mcp:" + serverName + "/");
                if (shouldRemove) {
                    // 根据你的 ToolRegistry 实现，可能需要不同的取消注册方法
                    // 如果没有 unregister 方法，可能需要其他方式处理
                    log.debug("移除MCP工具: {}", entry.getKey());
                }
                return shouldRemove;
            });

            client.disconnect();
            log.debug("已断开MCP服务器: {}", serverName);
        }
    }

    public List<String> getConnectedServers() {
        return new ArrayList<>(connectedServers.keySet());
    }

    public List<BaseTool> getServerTools(String serverName) {
        List<BaseTool> tools = new ArrayList<>();
        mcpTools.forEach((name, tool) -> {
            if (name.startsWith("mcp:" + serverName + "/")) {
                tools.add(tool);
            }
        });
        return tools;
    }

    public Map<String, BaseTool> getMCPTools() {
        return new HashMap<>(mcpTools);
    }

    public List<String> getAvailableToolNames() {
        return new ArrayList<>(mcpTools.keySet());
    }

    public void shutdown() {
        log.info("关闭所有MCP连接...");
        new ArrayList<>(connectedServers.keySet()).forEach(this::disconnectServer);
    }
}