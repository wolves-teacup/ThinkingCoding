package com.thinkingcoding.agentloop.v2.steer;

/**
 * Steering 控制器接口，接收来自 CLI/UI 的 steering 指令。
 */
public interface SteeringController {
    /**
     * 提交 steering 命令
     */
    void submit(SteeringCommand cmd);

    /**
     * 提交选项选择（用于 OptionManager）
     */
    void submitOptionSelection(int optionIndex);
}
