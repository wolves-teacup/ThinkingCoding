package com.thinkingcoding.ui.component;

import com.thinkingcoding.service.PerformanceMonitor;
import com.thinkingcoding.ui.AnsiColors;
import org.jline.terminal.Terminal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 状态栏组件，负责在终端中显示各种状态信息，如普通信息、错误、成功、警告、性能数据等
 */
public class StatusBar {
    private final Terminal terminal;
    private final DateTimeFormatter timeFormatter;

    public StatusBar(Terminal terminal) {
        this.terminal = terminal;
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    }

    public void showInfo(String info) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String message = String.format("%s[%s] ℹ️  %s%s",
                AnsiColors.BRIGHT_BLACK, timestamp, AnsiColors.BRIGHT_CYAN, info);

        terminal.writer().println(message + AnsiColors.RESET);
        terminal.writer().flush();
    }

    public void showError(String error) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String message = String.format("%s[%s] ❌ %s%s",
                AnsiColors.BRIGHT_BLACK, timestamp, AnsiColors.BRIGHT_RED, error);

        terminal.writer().println(message + AnsiColors.RESET);
        terminal.writer().flush();
    }

    public void showSuccess(String message) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String formatted = String.format("%s[%s] ✅ %s%s",
                AnsiColors.BRIGHT_BLACK, timestamp, AnsiColors.BRIGHT_GREEN, message);

        terminal.writer().println(formatted + AnsiColors.RESET);
        terminal.writer().flush();
    }

    public void showWarning(String warning) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String message = String.format("%s[%s] ⚠️  %s%s",
                AnsiColors.BRIGHT_BLACK, timestamp, AnsiColors.BRIGHT_YELLOW, warning);

        terminal.writer().println(message + AnsiColors.RESET);
        terminal.writer().flush();
    }

    public void showPerformanceInfo(PerformanceMonitor.PerformanceData data) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String message = String.format("%s[%s] 📊 Performance: %dms, %d tokens, %d tools%s",
                AnsiColors.BRIGHT_BLACK, timestamp,
                data.getExecutionTimeMs(), data.getTotalTokens(), data.getTotalToolCalls(),
                AnsiColors.RESET);

        terminal.writer().println(message);
        terminal.writer().flush();
    }

    public void showSessionInfo(String sessionId, int messageCount) {
        String shortId = sessionId.length() > 8 ? sessionId.substring(0, 8) + "..." : sessionId;
        String message = String.format("%s💬 Session: %s (%d messages)%s",
                AnsiColors.BRIGHT_BLUE, shortId, messageCount, AnsiColors.RESET);

        terminal.writer().println(message);
        terminal.writer().flush();
    }

    public void showModelInfo(String modelName) {
        String message = String.format("%s🤖 Model: %s%s",
                AnsiColors.BRIGHT_GREEN, modelName, AnsiColors.RESET);

        terminal.writer().println(message);
        terminal.writer().flush();
    }
}