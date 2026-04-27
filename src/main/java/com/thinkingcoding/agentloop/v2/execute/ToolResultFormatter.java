package com.thinkingcoding.agentloop.v2.execute;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkingcoding.model.ToolCall;
import com.thinkingcoding.model.ToolResult;

/**
 * 统一将工具执行结果写成可回填到 history 的 observation 文本。
 */
public class ToolResultFormatter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String formatSuccess(ToolCall toolCall, ToolResult result) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("Tool '")
                .append(toolCall.getToolName())
                .append("' executed successfully");

        if (toolCall.getParameters() != null && !toolCall.getParameters().isEmpty()) {
            formatted.append(" with parameters: ")
                    .append(convertParametersToJson(toolCall.getParameters()));
        }

        formatted.append("\n");

        String output = result.getOutput();
        if (output != null && !output.trim().isEmpty()) {
            formatted.append("Result:\n").append(output);
        } else {
            formatted.append("Operation completed successfully.");
        }

        return formatted.toString();
    }

    public String formatFailure(ToolCall toolCall, String errorMessage) {
        return "Tool execution failed for '" + toolCall.getToolName() + "': " + errorMessage;
    }

    public String convertParametersToJson(java.util.Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "{}";
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(parameters);
        } catch (Exception e) {
            StringBuilder json = new StringBuilder("{");
            parameters.forEach((key, value) -> json
                    .append("\"").append(key).append("\":\"").append(value).append("\","));
            if (json.length() > 1) {
                json.setLength(json.length() - 1);
            }
            json.append("}");
            return json.toString();
        }
    }
}

