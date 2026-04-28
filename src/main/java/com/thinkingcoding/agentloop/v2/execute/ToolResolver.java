package com.thinkingcoding.agentloop.v2.execute;

import com.thinkingcoding.model.ToolCall;
import com.thinkingcoding.tools.BaseTool;
import com.thinkingcoding.tools.ToolRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * 原生工具解析器：仅解析注册表中的真实工具名，不做别名映射。
 */
public class ToolResolver {

    public ResolvedTool resolve(ToolCall toolCall, ToolRegistry toolRegistry) {
        String requestedToolName = toolCall.getToolName();
        Map<String, Object> params = toolCall.getParameters() == null
                ? new HashMap<>()
                : new HashMap<>(toolCall.getParameters());

        BaseTool tool = toolRegistry.getTool(requestedToolName);
        return new ResolvedTool(requestedToolName, tool, params);
    }

    public record ResolvedTool(String executableToolName, BaseTool tool, Map<String, Object> parameters) {
    }
}

