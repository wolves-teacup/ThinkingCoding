package com.thinkingcoding.agentloop.v2.gateway;

import com.thinkingcoding.model.ChatMessage;

import java.util.List;

/**
 * 会话网关接口，适配 SessionService。
 */
public interface SessionGateway {
    /**
     * 加载会话历史
     */
    List<ChatMessage> load(String sessionId);

    /**
     * 保存会话历史
     */
    void save(String sessionId, List<ChatMessage> history);
}
