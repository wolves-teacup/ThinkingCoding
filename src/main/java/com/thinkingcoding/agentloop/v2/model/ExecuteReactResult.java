package com.thinkingcoding.agentloop.v2.model;

import com.thinkingcoding.agentloop.v2.execute.ToolExecutionOutcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Execute + ReAct 阶段的执行结果。
 */
public class ExecuteReactResult {
    private final int steps;
    private final List<ToolExecutionOutcome> trace;
    private final boolean cancelled;
    private final String finalAssistantText;

    public ExecuteReactResult(int steps, List<ToolExecutionOutcome> trace, 
                             boolean cancelled, String finalAssistantText) {
        this.steps = steps;
        this.trace = trace != null ? trace : Collections.emptyList();
        this.cancelled = cancelled;
        this.finalAssistantText = finalAssistantText;
    }

    public static ExecuteReactResult empty() {
        return new ExecuteReactResult(0, Collections.emptyList(), false, "");
    }

    public int getSteps() {
        return steps;
    }

    public List<ToolExecutionOutcome> getTrace() {
        return trace;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public String getFinalAssistantText() {
        return finalAssistantText;
    }

    public boolean hasExecutions() {
        return !trace.isEmpty();
    }
}
