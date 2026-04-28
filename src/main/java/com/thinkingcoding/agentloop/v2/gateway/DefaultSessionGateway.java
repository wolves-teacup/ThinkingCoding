package com.thinkingcoding.agentloop.v2.gateway;

import com.thinkingcoding.core.ThinkingCodingContext;
import com.thinkingcoding.model.ChatMessage;
import com.thinkingcoding.service.SessionService;

import java.util.List;

/**
 * SessionGateway 实现，委托给现有的 SessionService。
 */
public class DefaultSessionGateway implements SessionGateway {

    private final SessionService sessionService;

    public DefaultSessionGateway(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public List<ChatMessage> load(String sessionId) {
        return sessionService.loadSession(sessionId);
    }

    @Override
    public void save(String sessionId, List<ChatMessage> history) {
        sessionService.saveSession(sessionId, history);
    }
}
