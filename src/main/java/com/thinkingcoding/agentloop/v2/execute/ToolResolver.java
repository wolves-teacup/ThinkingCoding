package com.thinkingcoding.agentloop.v2.execute;

import com.thinkingcoding.model.ToolCall;
import com.thinkingcoding.tools.BaseTool;
import com.thinkingcoding.tools.ToolRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一处理语义化工具别名到实际可执行工具的映射。
 */
public class ToolResolver {

    public ResolvedTool resolve(ToolCall toolCall, ToolRegistry toolRegistry) {
        String requestedToolName = toolCall.getToolName();
        Map<String, Object> params = toolCall.getParameters() == null
                ? new HashMap<>()
                : new HashMap<>(toolCall.getParameters());

        BaseTool direct = toolRegistry.getTool(requestedToolName);
        if (direct != null) {
            return new ResolvedTool(requestedToolName, direct, params);
        }

        if ("write_file".equals(requestedToolName)) {
            BaseTool fileManager = toolRegistry.getTool("file_manager");
            params.put("command", "write");
            return new ResolvedTool("file_manager", fileManager, params);
        }

        if ("read_file".equals(requestedToolName)) {
            BaseTool fileManager = toolRegistry.getTool("file_manager");
            params.put("command", "read");
            return new ResolvedTool("file_manager", fileManager, params);
        }

        if ("list_directory".equals(requestedToolName)) {
            BaseTool fileManager = toolRegistry.getTool("file_manager");
            params.put("command", "list");
            return new ResolvedTool("file_manager", fileManager, params);
        }

        if ("bash".equals(requestedToolName)) {
            BaseTool commandExecutor = toolRegistry.getTool("command_executor");
            if (!params.containsKey("command") && params.containsKey("input")) {
                params.put("command", params.get("input"));
            }
            return new ResolvedTool("command_executor", commandExecutor, params);
        }

        return new ResolvedTool(requestedToolName, null, params);
    }

    public record ResolvedTool(String executableToolName, BaseTool tool, Map<String, Object> parameters) {
    }
}

