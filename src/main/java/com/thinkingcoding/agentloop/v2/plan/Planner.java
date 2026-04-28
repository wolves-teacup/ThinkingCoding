package com.thinkingcoding.agentloop.v2.plan;

import com.thinkingcoding.agentloop.v2.gateway.AgentEventSink;
import com.thinkingcoding.agentloop.v2.model.PlanRequest;
import com.thinkingcoding.agentloop.v2.model.PlanResult;
import com.thinkingcoding.agentloop.v2.steer.SteeringHandle;

/**
 * 规划器接口，负责生成任务计划和工具调用意图。
 */
public interface Planner {
    /**
     * 执行规划
     * 
     * @param request 规划请求
     * @param events 事件接收器
     * @param steering Steering 句柄
     * @return 规划结果
     */
    PlanResult plan(PlanRequest request, AgentEventSink events, SteeringHandle steering);
}
