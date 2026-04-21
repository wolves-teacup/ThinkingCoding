package com.thinkingcoding.ui.component;

import com.thinkingcoding.model.ToolCall;
import com.thinkingcoding.ui.AnsiColors;
import org.jline.terminal.Terminal;

/**
 * 工具调用显示组件，负责在终端中美观地展示工具调用的状态和结果
 */
public class ToolDisplay {
    private final Terminal terminal;

    public ToolDisplay(Terminal terminal) {
        this.terminal = terminal;
    }

    public void displayToolCall(ToolCall toolCall) {
        // 🔥 不再显示调试信息，保持界面简洁
        // 工具调用的详细信息已经在确认界面显示过了
    }

    public void displayToolStart(String toolName) {
        String message = String.format("%s🛠️  Starting tool: %s%s%s",
                AnsiColors.BRIGHT_MAGENTA, AnsiColors.BRIGHT_WHITE, toolName, AnsiColors.RESET);

        terminal.writer().println(message);
        terminal.writer().flush();
    }

    public void displayToolResult(String toolName, String result, boolean success, long executionTime) {
        String statusIcon = success ? "✅" : "❌";
        String statusColor = success ? AnsiColors.BRIGHT_GREEN : AnsiColors.BRIGHT_RED;

        String message = String.format("%s%s %s: %s (%dms)%s",
                statusColor, statusIcon, toolName, result, executionTime, AnsiColors.RESET);

        terminal.writer().println(message);
        terminal.writer().flush();
    }

    /**
     * 显示 Claude Code 风格的工具调用
     * 格式：⏺ Write(HelloWorld.java)
     */
    public void displayClaudeStyleToolCall(String toolName, String target, String result) {
        // 转换工具名称为友好的显示名
        String displayName = getFriendlyToolName(toolName);

        // 显示工具调用
        String callMessage = String.format("%s⏺ %s(%s)%s",
                AnsiColors.BRIGHT_CYAN, displayName, target, AnsiColors.RESET);
        terminal.writer().println(callMessage);

        // 显示结果（缩进显示）
        if (result != null && !result.isEmpty()) {
            String resultMessage = String.format("%s  ⎿ %s%s",
                    AnsiColors.BRIGHT_BLACK, result, AnsiColors.RESET);
            terminal.writer().println(resultMessage);
        }

        terminal.writer().flush();
    }

    /**
     * 将工具名称转换为友好的显示名
     */
    private String getFriendlyToolName(String toolName) {
        return switch (toolName) {
            case "write_file" -> "Write";
            case "read_file" -> "Read";
            case "execute_command" -> "Bash";
            case "list_directory" -> "List";
            case "edit_file" -> "Edit";
            default -> toolName;
        };
    }
}