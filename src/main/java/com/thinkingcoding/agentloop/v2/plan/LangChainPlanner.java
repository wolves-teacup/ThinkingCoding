package com.thinkingcoding.agentloop.v2.plan;

import com.thinkingcoding.agentloop.v2.gateway.AgentEventSink;
import com.thinkingcoding.agentloop.v2.model.PlanRequest;
import com.thinkingcoding.agentloop.v2.model.PlanResult;
import com.thinkingcoding.agentloop.v2.steer.SteeringHandle;
import com.thinkingcoding.core.OptionManager;
import com.thinkingcoding.core.ThinkingCodingContext;
import com.thinkingcoding.model.ChatMessage;
import com.thinkingcoding.model.ToolCall;
import com.thinkingcoding.service.AIService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 LangChainService 的规划器实现。
 * 复用现有的流式对话机制，提取 assistant 文本和 tool calls。
 */
public class LangChainPlanner implements Planner {

    /** 应用上下文，提供UI、工具注册表等核心组件的访问入口 */
    private final ThinkingCodingContext context;
    
    /** AI服务接口，负责执行流式对话和工具调用处理 */
    private final AIService aiService;
    
    /** 选项管理器，从AI响应中提取结构化选项供用户选择 */
    private final OptionManager optionManager;

    public LangChainPlanner(ThinkingCodingContext context) {
        this.context = context;
        this.aiService = context.getAiService();
        this.optionManager = new OptionManager();
    }

    /**
     * 执行规划，返回规划结果。
     *
     * @param request 规划请求
     * @param events  事件处理接口
     * @param steering 策略接口
     * @return 规划结果
     */
    @Override
    public PlanResult plan(PlanRequest request, AgentEventSink events, SteeringHandle steering) {
        try {
            // 准备历史消息（复制一份用于本次规划）
            List<ChatMessage> historyForPlanning = new ArrayList<>(request.getHistoryView());

            /**
             * AI响应结果收集器
             * 通过回调机制在流式对话过程中实时累积AI返回的文本片段和工具调用
             */
            final StringBuilder assistantTextBuilder = new StringBuilder();
            final List<ToolCall> collectedToolCalls = new ArrayList<>();
            final AtomicBoolean stopped = new AtomicBoolean(false);

            // 设置临时的消息处理器
            aiService.setMessageHandler(message -> {
                if (steering != null && steering.shouldStopGeneration()) {
                    stopped.set(true);
                    return;
                }

                // 只处理助手(AI)发出的消息
                if (message.isAssistantMessage()) {
                    String content = message.getContent();  // 获取消息内容
                    assistantTextBuilder.append(content);   // 将内容追加到StringBuilder中

                    // 通知事件系统有新token到达（用于UI实时更新显示）
                    events.onToken(content);

                    // 尝试从响应中提取选项（如1. xxx, 2. xxx等格式）
                    optionManager.extractOptionsFromResponse(content);
                }
            });

            // 设置临时的工具调用处理器
            aiService.setToolCallHandler(toolCall -> {
                if (!stopped.get()) {
                    collectedToolCalls.add(toolCall);
                }
            });

            // 执行流式对话
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                aiService.streamingChat(
                        request.getUserInput(),
                        historyForPlanning,
                        request.getModelName()
                );
            });

            // 等待完成（带超时）
            try {
                future.get(5, TimeUnit.MINUTES);
            } catch (Exception e) {
                System.err.println("⚠️  规划阶段等待超时或出错: " + e.getMessage());
                future.cancel(true);
            }

            // 构建 PlanResult
            String assistantText = assistantTextBuilder.toString().trim();

            // 通知事件层本次 assistant 输出已完成（用于流式收尾或兜底显示）。
            events.onAssistantMessage(assistantText);

            // 如果有选项，转换为 Option 列表
            List<OptionManager.Option> options = optionManager.getCurrentOptions();

            // 显示选项提示（如果有）
            if (!options.isEmpty()) {
                context.getUi().displayInfo("\n💡 请输入选项编号（1-" + options.size() + "）来选择你想要的操作");
            }

            return new PlanResult(
                    assistantText,
                    collectedToolCalls,
                    options,
                    stopped.get()
            );

        } catch (Exception e) {
            System.err.println("❌ 规划阶段异常: " + e.getMessage());
            e.printStackTrace();
            return PlanResult.empty();
        }
    }
}
