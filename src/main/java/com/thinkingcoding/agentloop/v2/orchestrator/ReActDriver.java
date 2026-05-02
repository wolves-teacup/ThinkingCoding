package com.thinkingcoding.agentloop.v2.orchestrator;

import com.thinkingcoding.agentloop.v2.execute.ToolExecutionEngine;
import com.thinkingcoding.agentloop.v2.execute.ToolExecutionOutcome;
import com.thinkingcoding.agentloop.v2.gateway.AgentEventSink;
import com.thinkingcoding.agentloop.v2.model.ExecuteReactResult;
import com.thinkingcoding.agentloop.v2.model.PlanRequest;
import com.thinkingcoding.agentloop.v2.model.PlanResult;
import com.thinkingcoding.agentloop.v2.model.TurnContext;
import com.thinkingcoding.agentloop.v2.plan.Planner;
import com.thinkingcoding.agentloop.v2.steer.SteeringHandle;
import com.thinkingcoding.agentloop.v2.steer.ToolConfirmationPolicy;
import com.thinkingcoding.core.ThinkingCodingContext;
import com.thinkingcoding.model.ChatMessage;
import com.thinkingcoding.model.ToolCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ReAct 驱动器，实现执行与反思的闭环。
 * 
 * 流程：
 * 1. 接收初始 PlanResult
 * 2. 对每个 ToolCall 执行 Steering 决策
 * 3. 执行工具并写回 observation
 * 4. 如果需要，触发 Planner 续跑（ReAct）
 * 5. 达到上限或无更多工具时退出
 */
public class ReActDriver {

    private final ThinkingCodingContext context;
    private final AgentConfig config;

    public ReActDriver(ThinkingCodingContext context, AgentConfig config) {
        this.context = context;
        this.config = config;
    }

    /**
     * 运行 ReAct 循环
     *
     * @param turn           回合上下文
     * @param initialPlan    初始规划结果
     * @param planner        规划器
     * @param engine         工具执行引擎
     * @param confirmPolicy  确认策略
     * @param events         事件接收器
     * @param steering       Steering 句柄
     * @return 执行结果
     */
    /**
     * 运行 ReAct 循环，执行规划并处理工具调用。
     *
     * @param turn           回合上下文，包含会话信息和历史记录
     * @param initialPlan    初始规划结果，包含助手文本和初始工具调用列表
     * @param planner        规划器，用于在 ReAct 过程中生成后续计划
     * @param engine         工具执行引擎，负责实际执行工具调用
     * @param confirmPolicy  工具确认策略，决定是否执行工具及 Steering 控制
     * @param events         事件接收器，用于发布执行过程中的事件
     * @param steering       Steering 句柄，用于控制循环的中断和取消
     * @return               执行结果，包含步数、执行轨迹、是否取消及最终的助手文本
     */
    public ExecuteReactResult run(
            TurnContext turn,
            PlanResult initialPlan,
            Planner planner,
            ToolExecutionEngine engine,
            ToolConfirmationPolicy confirmPolicy,
            AgentEventSink events,
            SteeringHandle steering
    ) {
        List<ToolExecutionOutcome> trace = new ArrayList<>();
        int steps = 0;
        boolean cancelled = false;
        String finalAssistantText = initialPlan.getAssistantText();

        // 初始化待执行的工具调用队列
        List<ToolCall> toolCalls = new ArrayList<>(initialPlan.getToolCalls());
        int toolCallIndex = 0;
        
        // 🔥 添加已执行工具的跟踪，防止重复执行相同工具调用
        Set<String> executedToolSignatures = new java.util.HashSet<>();
        Map<String, ChatMessage> cachedToolResults = new HashMap<>();

        // 启动 ReAct 主循环，受最大步数限制
        while (steps < config.getMaxReActStepsPerTurn()) {
            // 检查用户是否请求取消当前回合
            if (steering.shouldCancelTurn()) {
                cancelled = true;
                context.getUi().displayWarning("⚠️  回合已取消");
                break;
            }

            // 若队列为空则退出循环
            if (toolCallIndex >= toolCalls.size()) {
                break;
            }

            // 获取并准备执行下一个工具调用
            ToolCall currentToolCall = toolCalls.get(toolCallIndex);
            toolCallIndex++;
            
            // 🔥 检查是否为重复的工具调用（防止无限循环）
            String toolSignature = generateToolSignature(currentToolCall);
            if (executedToolSignatures.contains(toolSignature)) {
                context.getUi().displayWarning("⚠️  检测到重复的工具调用，复用缓存结果: " + currentToolCall.getToolName());
                ChatMessage cached = cachedToolResults.get(toolSignature);
                if (cached != null) {
                    turn.getHistory().add(new ChatMessage(cached));
                }
                continue;
            }

            // 检查是否达到单轮最大工具调用限制
            if (trace.size() >= config.getMaxToolCallsPerPlan()) {
                context.getUi().displayInfo("ℹ️  已达到本轮最大工具调用数 (" + config.getMaxToolCallsPerPlan() + ")");
                break;
            }

            // 通过执行引擎处理工具调用（含用户确认与 Steering 门禁）
            ToolExecutionOutcome outcome = engine.execute(
                    currentToolCall,
                    turn,
                    confirmPolicy,
                    events,
                    steering
            );

            trace.add(outcome);
            steps++;
            
            // 🔥 记录已执行的工具签名
            executedToolSignatures.add(toolSignature);

            // 将工具执行结果作为观察信息写回对话历史
            if (outcome.getHistoryMessageToAppend() != null) {
                ChatMessage historyMessage = outcome.getHistoryMessageToAppend();
                turn.getHistory().add(historyMessage);
                cachedToolResults.put(toolSignature, new ChatMessage(historyMessage));
            }

            // 若工具被拒绝执行，跳过后续逻辑并处理下一个调用
            if (!outcome.isExecuted()) {
                continue;
            }

            // 若队列中仍有缓存的工具调用，则继续执行而不触发重新规划
            if (toolCallIndex < toolCalls.size()) {
                continue;
            }

            // 触发 Planner 进行续跑规划（ReAct 核心：基于观察结果决定下一步）
            PlanRequest continuationRequest = new PlanRequest(
                    turn.getSessionId(),
                    turn.getModelName(),
                    "基于以上工具执行结果，请继续完成任务。如果任务已完成，请总结结果。",
                    turn.getHistory(),
                    true
            );

            PlanResult continuationPlan = planner.plan(continuationRequest, events, steering);
            
            // 若规划阶段被停止，则标记取消并退出
            if (continuationPlan.isStopped()) {
                cancelled = true;
                break;
            }

            // 更新最终返回的助手文本
            if (!continuationPlan.getAssistantText().isEmpty()) {
                finalAssistantText = continuationPlan.getAssistantText();
            }

            // 将新规划产生的工具调用加入执行队列
            if (continuationPlan.hasToolCalls()) {
                toolCalls.addAll(continuationPlan.getToolCalls());
            } else {
                // 若无新工具调用，说明任务可能已完成或无需进一步操作
                break;
            }
        }

        // 若因达到最大步数限制而退出，发出警告提示
        if (steps >= config.getMaxReActStepsPerTurn()) {
            context.getUi().displayWarning("⚠️  已达到最大 ReAct 步数限制 (" + config.getMaxReActStepsPerTurn() + ")");
        }

        return new ExecuteReactResult(steps, trace, cancelled, finalAssistantText);
    }
    
    /**
     * 🔥 生成工具调用的唯一签名，用于检测重复调用
     */
    private String generateToolSignature(ToolCall toolCall) {
        StringBuilder signature = new StringBuilder();
        signature.append(toolCall.getToolName());
        
        if (toolCall.getParameters() != null && !toolCall.getParameters().isEmpty()) {
            signature.append("_");
            // 按参数名排序以确保一致性
            List<String> sortedKeys = new ArrayList<>(toolCall.getParameters().keySet());
            java.util.Collections.sort(sortedKeys);
            
            for (String key : sortedKeys) {
                signature.append(key).append("=").append(toolCall.getParameters().get(key)).append(";");
            }
        }
        
        return signature.toString();
    }
}
