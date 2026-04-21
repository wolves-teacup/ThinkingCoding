package com.thinkingcoding.service;

import com.thinkingcoding.model.ChatMessage;
import com.thinkingcoding.model.ToolCall;

import java.util.List;
import java.util.function.Consumer;

/**
 * AI 服务接口，定义了与 AI 模型交互的方法
 */
public interface AIService {
    List<ChatMessage> chat(String input, List<ChatMessage> history, String modelName);
    List<ChatMessage> streamingChat(String input, List<ChatMessage> history, String modelName);
    void setMessageHandler(Consumer<ChatMessage> handler);
    void setToolCallHandler(Consumer<ToolCall> handler);
    boolean validateModel(String modelName);
    List<String> getAvailableModels();
}