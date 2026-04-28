package com.thinkingcoding.agentloop.v2.steer;

import com.thinkingcoding.model.ToolExecution;

/**
 * 工具确认策略接口。
 * 负责决定工具是否应该被执行。
 */
public interface ToolConfirmationPolicy {
    /**
     * 决策是否执行工具
     * 
     * @param execution 工具执行信息
     * @param steering Steering 句柄
     * @return 决策结果
     */
    ToolDecision decide(ToolExecution execution, SteeringHandle steering);
}
