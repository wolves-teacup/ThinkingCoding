package com.thinkingcoding.core;

import com.thinkingcoding.model.ChatMessage;

import java.util.function.Consumer;

/**
 * StreamingOutput类用于处理流式输出，将生成的内容逐步传递给消息处理器。
 * 支持暂停/停止生成功能。
 */
public class StreamingOutput {
    private final Consumer<ChatMessage> messageHandler;
    private final StringBuilder currentContent;
    private String lastSentContent = "";
    private volatile boolean stopped = false; // 停止标志
    private volatile boolean paused = false;  // 暂停标志


    public StreamingOutput(Consumer<ChatMessage> messageHandler) {
        this.messageHandler = messageHandler;
        this.currentContent = new StringBuilder();
    }

    /**
     * 停止生成
     */
    public void stop() {
        this.stopped = true;
        System.out.println("\n⏸️  用户已停止生成");
    }

    /**
     * 暂停生成
     */
    public void pause() {
        this.paused = true;
        System.out.println("\n⏸️  生成已暂停");
    }

    /**
     * 恢复生成
     */
    public void resume() {
        this.paused = false;
        System.out.println("\n▶️  生成已恢复");
    }

    /**
     * 检查是否已停止
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * 检查是否已暂停
     */
    public boolean isPaused() {
        return paused;
    }

    public void appendContent(String token) {
        // 如果已停止，立即返回，不再处理任何 token
        if (stopped) {
            return;
        }

        // 如果暂停，等待恢复
        while (paused && !stopped) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // 再次检查是否在等待期间被停止
        if (stopped) {
            return;
        }

        // 累积内容
        currentContent.append(token);

        // 直接传递token
        ChatMessage tokenMessage = new ChatMessage("assistant", token);
        messageHandler.accept(tokenMessage);
    }

    public void complete() {
        // 如果被停止，发送截断消息并立即返回
        if (stopped) {
            ChatMessage stopMessage = new ChatMessage("assistant",
                "\n\n💡 [生成已被用户停止，未显示后续内容]");
            messageHandler.accept(stopMessage);
            reset();
            return;
        }

        // 正常完成时发送最终消息
        if (currentContent.length() > 0) {
            ChatMessage finalMessage = ChatMessage.from(currentContent.toString());
            messageHandler.accept(finalMessage);
        }

        // Reset state
        reset();
    }

    public void reset() {
        currentContent.setLength(0);
        lastSentContent = "";
        stopped = false;
        paused = false;
    }
}