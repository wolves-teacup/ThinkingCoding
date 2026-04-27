package com.thinkingcoding.agentloop.v2.execute;

import com.thinkingcoding.agentloop.v2.gateway.AgentEventSink;
import com.thinkingcoding.agentloop.v2.model.TurnContext;
import com.thinkingcoding.agentloop.v2.steer.SteeringHandle;
import com.thinkingcoding.agentloop.v2.steer.ToolConfirmationPolicy;
import com.thinkingcoding.model.ToolCall;

public interface ToolExecutionEngine {
    ToolExecutionOutcome execute(
            ToolCall call,
            TurnContext turn,
            ToolConfirmationPolicy confirmationPolicy,
            AgentEventSink events,
            SteeringHandle steering
    );
}

