package com.thinkingcoding.agentloop.v2.steer;

/**
 * Steering 命令枚举，用于中途干预。
 */
public enum SteeringCommand {
    /** 停止当前流式生成 */
    STOP_GENERATION,
    
    /** 取消当前回合（停止后续执行） */
    CANCEL_TURN,
    
    /** 开启自动批准模式 */
    SET_AUTO_APPROVE_ON,
    
    /** 关闭自动批准模式 */
    SET_AUTO_APPROVE_OFF,
    
    /** 跳过下一个工具调用 */
    SKIP_NEXT_TOOL
}
