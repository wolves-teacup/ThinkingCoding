package com.thinkingcoding.agentloop.v2.steer;

/**
 * 工具确认决策枚举。
 */
public enum ToolDecision {
    /** 执行工具 */
    EXECUTE,
    
    /** 执行并继续跟进（可能需要 ReAct 循环） */
    EXECUTE_AND_FOLLOWUP,
    
    /** 丢弃/取消执行 */
    DISCARD
}
