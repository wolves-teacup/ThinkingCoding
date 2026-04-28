package com.thinkingcoding.agentloop.v2.gateway;

import com.thinkingcoding.agentloop.v2.execute.ToolExecutionOutcome;
import com.thinkingcoding.model.ToolCall;

/**
 * Agent 事件接收器接口，统一输出事件到 UI。
 */
public interface AgentEventSink {
    /**
     * 收到 token（流式输出）
     */
    void onToken(String token);

    /**
     * 收到完整的 assistant 消息
     */
    void onAssistantMessage(String text);

    /**
     * 工具被计划执行
     */
    void onToolPlanned(ToolCall call);

    /**
     * 工具执行完成
     */
    void onToolExecuted(ToolExecutionOutcome outcome);
}
