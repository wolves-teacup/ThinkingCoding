package com.thinkingcoding.agentloop.v2.model;

import com.thinkingcoding.core.OptionManager;
import com.thinkingcoding.model.ToolCall;

import java.util.Collections;
import java.util.List;

/**
 * 规划阶段的输出结果。
 */
public class PlanResult {
    private final String assistantText;
    private final List<ToolCall> toolCalls;
    private final List<OptionManager.Option> options;
    private final boolean stopped;

    public PlanResult(String assistantText, List<ToolCall> toolCalls, 
                     List<OptionManager.Option> options, boolean stopped) {
        this.assistantText = assistantText;
        this.toolCalls = toolCalls != null ? toolCalls : Collections.emptyList();
        this.options = options != null ? options : Collections.emptyList();
        this.stopped = stopped;
    }

    public static PlanResult empty() {
        return new PlanResult("", Collections.emptyList(), Collections.emptyList(), false);
    }

    public String getAssistantText() {
        return assistantText;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public List<OptionManager.Option> getOptions() {
        return options;
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasOptions() {
        return options != null && !options.isEmpty();
    }
}
