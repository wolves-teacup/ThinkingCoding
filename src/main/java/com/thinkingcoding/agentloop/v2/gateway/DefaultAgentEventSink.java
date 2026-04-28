package com.thinkingcoding.agentloop.v2.gateway;

import com.thinkingcoding.agentloop.v2.execute.ToolExecutionOutcome;
import com.thinkingcoding.core.ThinkingCodingContext;
import com.thinkingcoding.model.ChatMessage;
import com.thinkingcoding.model.ToolCall;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AgentEventSink 实现，直接调用 ThinkingCodingUI。
 */
public class DefaultAgentEventSink implements AgentEventSink {

    private final ThinkingCodingContext context;
    private final AtomicBoolean streamedThisTurn = new AtomicBoolean(false);

    public DefaultAgentEventSink(ThinkingCodingContext context) {
        this.context = context;
    }

    @Override
    public void onToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        streamedThisTurn.set(true);
        context.getUi().displayAIMessage(new ChatMessage("assistant", token));
    }

    @Override
    public void onAssistantMessage(String text) {
        // 若本轮已进行过 token 流式输出，仅重置状态，避免整段重复打印。
        if (streamedThisTurn.getAndSet(false)) {
            return;
        }

        // 兜底：若未收到 token（如上游返回整段文本），仍显示完整消息。
        if (text != null && !text.isEmpty()) {
            context.getUi().displayAIMessage(new ChatMessage("assistant", text));
        }
    }

    @Override
    public void onToolPlanned(ToolCall call) {
        // 工具计划执行通知（已在流式输出中显示）
        // context.getUi().displayInfo("🔧 计划执行: " + call.getToolName());
    }

    @Override
    public void onToolExecuted(ToolExecutionOutcome outcome) {
        // 工具执行结果已由 DefaultToolExecutionEngine 显示
        // 这里可以添加额外的汇总逻辑
        if (outcome.isExecuted()) {
            // 可以在这里添加执行后的额外处理
        }
    }
}
