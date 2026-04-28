package com.thinkingcoding.agentloop.v2.steer;

import com.thinkingcoding.core.ToolExecutionConfirmation;
import com.thinkingcoding.model.ToolExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * InteractiveToolConfirmationPolicy 单元测试
 * 验证工具确认策略的行为
 */
public class ToolConfirmationPolicyTest {

    private InteractiveToolConfirmationPolicy policy;

    @Mock
    private ToolExecutionConfirmation legacyConfirmation;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        policy = new InteractiveToolConfirmationPolicy(legacyConfirmation);
    }

    @Test
    public void testAutoApproveEnabled() {
        // 准备
        policy.setAutoApprove(true);
        ToolExecution execution = createTestExecution();

        // 执行
        ToolDecision decision = policy.decide(execution, policy);

        // 验证 - 自动批准模式下应直接执行
        assertEquals(ToolDecision.EXECUTE_AND_FOLLOWUP, decision);
        verify(legacyConfirmation, never()).askConfirmationWithOptions(any());
    }

    @Test
    public void testCancelTurn() {
        // 准备
        policy.handleCommand(SteeringCommand.CANCEL_TURN);
        ToolExecution execution = createTestExecution();

        // 执行
        ToolDecision decision = policy.decide(execution, policy);

        // 验证 - 取消回合时应丢弃
        assertEquals(ToolDecision.DISCARD, decision);
    }

    @Test
    public void testSkipNextTool() {
        // 准备
        policy.handleCommand(SteeringCommand.SKIP_NEXT_TOOL);
        ToolExecution execution = createTestExecution();

        // 执行
        ToolDecision decision = policy.decide(execution, policy);

        // 验证 - 跳过下一个工具
        assertEquals(ToolDecision.DISCARD, decision);
        assertFalse(policy.shouldSkipNextTool()); // 标志应被重置
    }

    @Test
    public void testInteractiveConfirmCreateOnly() {
        // 准备
        policy.setAutoApprove(false);
        when(legacyConfirmation.askConfirmationWithOptions(any()))
            .thenReturn(ToolExecutionConfirmation.ActionType.CREATE_ONLY);
        
        ToolExecution execution = createTestExecution();

        // 执行
        ToolDecision decision = policy.decide(execution, policy);

        // 验证
        assertEquals(ToolDecision.EXECUTE, decision);
        verify(legacyConfirmation).askConfirmationWithOptions(execution);
    }

    @Test
    public void testInteractiveConfirmCreateAndRun() {
        // 准备
        policy.setAutoApprove(false);
        when(legacyConfirmation.askConfirmationWithOptions(any()))
            .thenReturn(ToolExecutionConfirmation.ActionType.CREATE_AND_RUN);
        
        ToolExecution execution = createTestExecution();

        // 执行
        ToolDecision decision = policy.decide(execution, policy);

        // 验证
        assertEquals(ToolDecision.EXECUTE_AND_FOLLOWUP, decision);
    }

    @Test
    public void testInteractiveConfirmDiscard() {
        // 准备
        policy.setAutoApprove(false);
        when(legacyConfirmation.askConfirmationWithOptions(any()))
            .thenReturn(ToolExecutionConfirmation.ActionType.DISCARD);
        
        ToolExecution execution = createTestExecution();

        // 执行
        ToolDecision decision = policy.decide(execution, policy);

        // 验证
        assertEquals(ToolDecision.DISCARD, decision);
    }

    @Test
    public void testSteeringCommands() {
        // 测试各种 steering 命令
        policy.handleCommand(SteeringCommand.STOP_GENERATION);
        assertTrue(policy.shouldStopGeneration());

        policy.handleCommand(SteeringCommand.SET_AUTO_APPROVE_ON);
        assertTrue(policy.isAutoApprove());

        policy.handleCommand(SteeringCommand.SET_AUTO_APPROVE_OFF);
        assertFalse(policy.isAutoApprove());
    }

    @Test
    public void testReset() {
        // 准备 - 设置一些状态
        policy.handleCommand(SteeringCommand.STOP_GENERATION);
        policy.handleCommand(SteeringCommand.SKIP_NEXT_TOOL);

        // 执行
        policy.reset();

        // 验证 - 状态应被重置（除了 autoApprove）
        assertFalse(policy.shouldStopGeneration());
        assertFalse(policy.shouldSkipNextTool());
        assertFalse(policy.shouldCancelTurn());
    }

    private ToolExecution createTestExecution() {
        Map<String, Object> params = new HashMap<>();
        params.put("path", "test.txt");
        return new ToolExecution("write_file", "写入文件", params, true);
    }
}
