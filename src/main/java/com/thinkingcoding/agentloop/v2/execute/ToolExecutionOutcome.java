package com.thinkingcoding.agentloop.v2.execute;

import com.thinkingcoding.model.ChatMessage;
import com.thinkingcoding.model.ToolCall;
import com.thinkingcoding.model.ToolResult;

public class ToolExecutionOutcome {
    private final ToolCall call;
    private final ToolResult result;
    private final ChatMessage historyMessageToAppend;
    private final boolean executed;

    public ToolExecutionOutcome(ToolCall call, ToolResult result, ChatMessage historyMessageToAppend, boolean executed) {
        this.call = call;
        this.result = result;
        this.historyMessageToAppend = historyMessageToAppend;
        this.executed = executed;
    }

    public ToolCall getCall() {
        return call;
    }

    public ToolResult getResult() {
        return result;
    }

    public ChatMessage getHistoryMessageToAppend() {
        return historyMessageToAppend;
    }

    public boolean isExecuted() {
        return executed;
    }
}

