package com.thinkingcoding.agentloop.v2.model;

import com.thinkingcoding.model.ChatMessage;

import java.util.List;

/**
 * 规划阶段的请求对象。
 */
public class PlanRequest {
    /** 会话唯一标识符，用于追踪和关联规划请求 */
    private final String sessionId;
    
    /** AI模型名称，指定执行规划时使用的语言模型 */
    private final String modelName;
    
    /** 用户当前输入内容，作为规划的主要意图来源 */
    private final String userInput;
    
    /** 历史消息视图，提供对话上下文供AI进行连续推理 */
    private final List<ChatMessage> historyView;
    
    /** 是否为续跑标志，区分首次规划和基于工具执行结果的再次规划 */
    private final boolean isContinuation;

    public PlanRequest(String sessionId, String modelName, String userInput, 
                      List<ChatMessage> historyView, boolean isContinuation) {
        this.sessionId = sessionId;
        this.modelName = modelName;
        this.userInput = userInput;
        this.historyView = historyView;
        this.isContinuation = isContinuation;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getModelName() {
        return modelName;
    }

    public String getUserInput() {
        return userInput;
    }

    public List<ChatMessage> getHistoryView() {
        return historyView;
    }

    public boolean isContinuation() {
        return isContinuation;
    }
}
