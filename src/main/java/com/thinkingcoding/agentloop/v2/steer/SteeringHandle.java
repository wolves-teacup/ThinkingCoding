package com.thinkingcoding.agentloop.v2.steer;

/**
 * Steering 句柄接口，供 Planner/Executor 查询 steering 状态。
 */
public interface SteeringHandle {
    /**
     * 是否应该停止生成
     */
    boolean shouldStopGeneration();

    /**
     * 是否处于自动批准模式
     */
    boolean isAutoApprove();

    /**
     * 是否应该取消当前回合
     */
    boolean shouldCancelTurn();

    /**
     * 是否应该跳过下一个工具调用
     */
    boolean shouldSkipNextTool();

    /**
     * 重置 skip 标志（在一次使用后清除）
     */
    void resetSkipFlag();
}
