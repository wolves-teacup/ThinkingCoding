package com.thinkingcoding.ui.component;

import com.thinkingcoding.model.ChatMessage;
import com.thinkingcoding.ui.AnsiColors;
import org.jline.terminal.Terminal;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 聊天渲染组件，负责在终端中美观地展示用户和AI的消息，以及会话列表
 */
public class ChatRenderer {
    private final Terminal terminal;
    private final DateTimeFormatter timeFormatter;

    public ChatRenderer(Terminal terminal) {
        this.terminal = terminal;
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    }

    public void renderUserMessage(ChatMessage message) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String formattedMessage = String.format("%s[%s] %sYou:%s %s",
                AnsiColors.BRIGHT_BLACK, timestamp, AnsiColors.BRIGHT_BLUE, AnsiColors.RESET, message.getContent());

        terminal.writer().println(formattedMessage);
        terminal.writer().flush();
    }

    public void renderAIMessage(ChatMessage message) {
        String timestamp = String.format(String.valueOf(timeFormatter));
        String formattedMessage = String.format("%s[%s] %sAI:%s %s",
                AnsiColors.BRIGHT_BLACK, timestamp, AnsiColors.BRIGHT_GREEN, AnsiColors.RESET, message.getContent());

        terminal.writer().println(formattedMessage);
        terminal.writer().flush();
    }

    public void renderSessionList(List<String> sessions) {
        if (sessions.isEmpty()) {
            terminal.writer().println(AnsiColors.YELLOW + "No sessions found." + AnsiColors.RESET);
            return;
        }

        terminal.writer().println(AnsiColors.BRIGHT_CYAN + "📚 Session List:" + AnsiColors.RESET);
        terminal.writer().println(AnsiColors.BRIGHT_BLACK + "========================" + AnsiColors.RESET);

        for (int i = 0; i < sessions.size(); i++) {
            String sessionId = sessions.get(i);
            String shortId = sessionId.length() > 8 ? sessionId.substring(0, 8) + "..." : sessionId;

            String line = String.format("%s%d. %s%s %s(%s)%s",
                    AnsiColors.BRIGHT_WHITE, i + 1,
                    AnsiColors.BRIGHT_GREEN, "🟢",
                    AnsiColors.BRIGHT_BLACK, shortId, AnsiColors.RESET);

            terminal.writer().println(line);
        }

        terminal.writer().flush();
    }

    public void renderThinking() {
        terminal.writer().print(AnsiColors.BRIGHT_YELLOW + "🤔 Thinking..." + AnsiColors.RESET);
        terminal.writer().flush();
    }

    public void clearThinking() {
        // 清除思考提示
        terminal.writer().print("\r" + " ".repeat(20) + "\r");
        terminal.writer().flush();
    }

    public void renderStreamingContent(String content) {
        // 用于流式输出的渲染
        terminal.writer().print("\r" + AnsiColors.BRIGHT_GREEN + "AI: " + AnsiColors.RESET + content);
        terminal.writer().flush();
    }

    public void completeStreaming() {
        terminal.writer().println();
        terminal.writer().flush();
    }
}