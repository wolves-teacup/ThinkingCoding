package com.thinkingcoding.agentloop.v2.steer;

import com.thinkingcoding.core.ToolExecutionConfirmation;
import com.thinkingcoding.model.ToolExecution;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 交互式工具确认策略实现。
 * 包装 legacy ToolExecutionConfirmation，将用户交互转换为 ToolDecision。
 */
public class InteractiveToolConfirmationPolicy implements ToolConfirmationPolicy, SteeringHandle {

    private final ToolExecutionConfirmation legacyConfirmation;
    
    // Steering 状态
    private final AtomicBoolean stopGeneration = new AtomicBoolean(false);
    private final AtomicBoolean cancelTurn = new AtomicBoolean(false);
    private final AtomicBoolean autoApprove = new AtomicBoolean(false);
    private final AtomicBoolean skipNextTool = new AtomicBoolean(false);

    public InteractiveToolConfirmationPolicy(ToolExecutionConfirmation legacyConfirmation) {
        this.legacyConfirmation = legacyConfirmation;
    }

    @Override
    public ToolDecision decide(ToolExecution execution, SteeringHandle steering) {
        // 检查是否应该取消回合
        if (shouldCancelTurn()) {
            return ToolDecision.DISCARD;
        }

        // 检查是否应该跳过下一个工具
        if (shouldSkipNextTool()) {
            resetSkipFlag();
            return ToolDecision.DISCARD;
        }

        // 如果处于自动批准模式，直接执行
        if (isAutoApprove()) {
            return ToolDecision.EXECUTE_AND_FOLLOWUP;
        }

        // 否则，使用交互式确认
        ToolExecutionConfirmation.ActionType action = legacyConfirmation.askConfirmationWithOptions(execution);

        switch (action) {
            case CREATE_ONLY:
                return ToolDecision.EXECUTE;
                
            case CREATE_AND_RUN:
                return ToolDecision.EXECUTE_AND_FOLLOWUP;
                
            case DISCARD:
                return ToolDecision.DISCARD;
                
            default:
                return ToolDecision.DISCARD;
        }
    }

    @Override
    public boolean shouldStopGeneration() {
        return stopGeneration.get();
    }

    @Override
    public boolean isAutoApprove() {
        return autoApprove.get();
    }

    @Override
    public boolean shouldCancelTurn() {
        return cancelTurn.get();
    }

    @Override
    public boolean shouldSkipNextTool() {
        return skipNextTool.get();
    }

    @Override
    public void resetSkipFlag() {
        skipNextTool.set(false);
    }

    /**
     * 处理 Steering 命令
     */
    public void handleCommand(SteeringCommand cmd) {
        switch (cmd) {
            case STOP_GENERATION:
                stopGeneration.set(true);
                break;
                
            case CANCEL_TURN:
                cancelTurn.set(true);
                stopGeneration.set(true);
                break;
                
            case SET_AUTO_APPROVE_ON:
                autoApprove.set(true);
                break;
                
            case SET_AUTO_APPROVE_OFF:
                autoApprove.set(false);
                break;
                
            case SKIP_NEXT_TOOL:
                skipNextTool.set(true);
                break;
        }
    }

    /**
     * 重置所有状态（新回合开始时调用）
     */
    public void reset() {
        stopGeneration.set(false);
        cancelTurn.set(false);
        skipNextTool.set(false);
        // 注意：autoApprove 不重置，保持用户设置
    }

    /**
     * 设置自动批准模式（用于批量操作）
     */
    public void setAutoApprove(boolean enabled) {
        autoApprove.set(enabled);
    }
}
