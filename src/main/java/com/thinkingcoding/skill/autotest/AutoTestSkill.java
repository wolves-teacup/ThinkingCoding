package com.thinkingcoding.skill.autotest;

import com.thinkingcoding.config.AppConfig;
import com.thinkingcoding.model.ChatMessage;
import com.thinkingcoding.model.ToolResult;
import com.thinkingcoding.service.AIService;
import com.thinkingcoding.skill.Skill;
import com.thinkingcoding.skill.SkillExecutionContext;
import com.thinkingcoding.skill.SkillResult;
import com.thinkingcoding.tools.BaseTool;
import com.thinkingcoding.tools.ToolRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto test generation and repair loop.
 */
public class AutoTestSkill implements Skill {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:java)?\\s*([\\s\\S]*?)```");

    private final AIService aiService;
    private final ToolRegistry toolRegistry;
    private final AppConfig appConfig;

    public AutoTestSkill(AIService aiService, ToolRegistry toolRegistry, AppConfig appConfig) {
        this.aiService = aiService;
        this.toolRegistry = toolRegistry;
        this.appConfig = appConfig;
    }

    @Override
    public String getName() {
        return "autotest";
    }

    @Override
    public SkillResult execute(SkillExecutionContext context) {
        Path sourcePath = Paths.get(context.getSourcePath()).toAbsolutePath().normalize();
        if (!Files.exists(sourcePath)) {
            return SkillResult.failure("Source file not found: " + sourcePath, null, null, 0);
        }

        try {
            String sourceCode = Files.readString(sourcePath);
            String testPathString = context.getTestPath();
            if (testPathString == null || testPathString.isBlank()) {
                testPathString = deriveTestPath(sourcePath, sourceCode).toString();
            }

            Path testPath = Paths.get(testPathString).toAbsolutePath().normalize();
            Files.createDirectories(testPath.getParent());

            String model = (context.getModel() == null || context.getModel().isBlank())
                    ? appConfig.getDefaultModel() : context.getModel();

            String generatedTest = generateInitialTest(sourcePath.toString(), sourceCode, testPath.toString(), model);
            Files.writeString(testPath, generatedTest);

            int maxRetries = Math.max(1, context.getMaxRetries());
            String mavenTestName = testPath.getFileName().toString().replace(".java", "");
            String lastError = null;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                ToolResult runResult = runTargetTest(mavenTestName);
                if (runResult.isSuccess()) {
                    return SkillResult.success("AutoTest skill finished successfully.", testPath.toString(), attempt);
                }

                lastError = shrinkLog(runResult.getError() != null ? runResult.getError() : runResult.getOutput());
                if (attempt == maxRetries) {
                    break;
                }

                String currentTestCode = Files.readString(testPath);
                String fixedTest = fixTest(sourcePath.toString(), sourceCode, testPath.toString(), currentTestCode, lastError, model);
                Files.writeString(testPath, fixedTest);
            }

            return SkillResult.failure("AutoTest skill failed after retries.", testPath.toString(), lastError, maxRetries);
        } catch (Exception e) {
            return SkillResult.failure("AutoTest skill failed: " + e.getMessage(), null, null, 0);
        }
    }

    private String generateInitialTest(String sourcePath, String sourceCode, String testPath, String model) {
        String prompt = TestPromptBuilder.buildInitialPrompt(sourcePath, sourceCode, testPath);
        String raw = askModel(prompt, model);
        return extractJavaCodeOrFallback(raw, sourceCode, testPath);
    }

    private String fixTest(String sourcePath, String sourceCode, String testPath, String currentTestCode, String errorLog, String model) {
        String prompt = TestPromptBuilder.buildFixPrompt(sourcePath, sourceCode, testPath, currentTestCode, errorLog);
        String raw = askModel(prompt, model);
        return extractJavaCodeOrFallback(raw, sourceCode, testPath);
    }

    private String askModel(String prompt, String model) {
        StringBuilder content = new StringBuilder();
        aiService.setMessageHandler(message -> {
            if (message != null && "assistant".equals(message.getRole()) && message.getContent() != null) {
                content.append(message.getContent());
            }
        });
        aiService.setToolCallHandler(toolCall -> {
            // Skill output is text-only; ignore tool calls from the model here.
        });
        aiService.streamingChat(prompt, new ArrayList<ChatMessage>(), model);
        return content.toString();
    }

    private ToolResult runTargetTest(String testClassName) {
        BaseTool commandExecutor = toolRegistry.getTool("command_executor");
        if (commandExecutor == null) {
            return ToolResult.error("command_executor tool is not available", 0);
        }
        String command = "mvn -Dtest=" + testClassName + " test";
        return commandExecutor.execute(command);
    }

    private Path deriveTestPath(Path sourcePath, String sourceCode) {
        String packageName = extractPackageName(sourceCode);
        String className = extractClassName(sourceCode);
        if (className == null || className.isBlank()) {
            className = sourcePath.getFileName().toString().replace(".java", "");
        }

        String relative = packageName.isBlank()
                ? className + "AutoTest.java"
                : packageName.replace('.', '/') + "/" + className + "AutoTest.java";

        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        return projectRoot.resolve("src/test/java").resolve(relative);
    }

    private String extractPackageName(String sourceCode) {
        Matcher matcher = PACKAGE_PATTERN.matcher(sourceCode);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractClassName(String sourceCode) {
        Matcher matcher = CLASS_PATTERN.matcher(sourceCode);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractJavaCodeOrFallback(String modelOutput, String sourceCode, String testPath) {
        if (modelOutput != null) {
            Matcher matcher = CODE_BLOCK_PATTERN.matcher(modelOutput);
            if (matcher.find()) {
                return matcher.group(1).trim() + System.lineSeparator();
            }
            if (modelOutput.contains("class ")) {
                return modelOutput.trim() + System.lineSeparator();
            }
        }
        return buildFallbackTest(sourceCode, testPath);
    }

    private String buildFallbackTest(String sourceCode, String testPath) {
        String packageName = extractPackageName(sourceCode);
        String testClassName = Paths.get(testPath).getFileName().toString().replace(".java", "");
        StringBuilder sb = new StringBuilder();
        if (!packageName.isBlank()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        sb.append("import org.junit.jupiter.api.Test;\n")
          .append("import static org.junit.jupiter.api.Assertions.assertTrue;\n\n")
          .append("class ").append(testClassName).append(" {\n")
          .append("    @Test\n")
          .append("    void generatedPlaceholder() {\n")
          .append("        assertTrue(true);\n")
          .append("    }\n")
          .append("}\n");
        return sb.toString();
    }

    private String shrinkLog(String log) {
        if (log == null) {
            return "";
        }
        int limit = 8000;
        if (log.length() <= limit) {
            return log;
        }
        return log.substring(log.length() - limit);
    }
}

