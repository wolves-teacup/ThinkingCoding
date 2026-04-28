package com.thinkingcoding.agentloop.v2.orchestrator;

import com.thinkingcoding.core.AgentLoop;
import com.thinkingcoding.core.ThinkingCodingContext;

/**
 * AgentLoop 工厂，根据配置创建 legacy 或 V2 版本的 AgentLoop。
 * 
 * 支持运行时切换，便于灰度发布和回滚。
 */
public class AgentLoopFactory {

    public enum AgentLoopVersion {
        LEGACY,  // 使用 core.AgentLoop
        V2       // 使用 v2.AgentOrchestrator（包装为 AgentLoop 接口）
    }

    private final ThinkingCodingContext context;
    private AgentLoopVersion currentVersion;
    private AgentConfig v2Config;

    public AgentLoopFactory(ThinkingCodingContext context) {
        this.context = context;
        this.currentVersion = AgentLoopVersion.LEGACY; // 默认使用 legacy
        this.v2Config = AgentConfig.defaultConfig();
    }

    /**
     * 创建 AgentLoop 实例
     * 
     * @param sessionId 会话 ID
     * @param modelName 模型名称
     * @return AgentLoop 实例（可能是 legacy 或 V2）
     */
    public Object createAgentLoop(String sessionId, String modelName) {
        switch (currentVersion) {
            case LEGACY:
                return new AgentLoop(context, sessionId, modelName);
                
            case V2:
                return new AgentOrchestrator(context, sessionId, modelName, v2Config);
                
            default:
                throw new IllegalStateException("Unknown agent loop version: " + currentVersion);
        }
    }

    /**
     * 设置使用的版本
     */
    public void setVersion(AgentLoopVersion version) {
        this.currentVersion = version;
        System.out.println("✅ AgentLoop 版本已切换为: " + version);
    }

    /**
     * 获取当前版本
     */
    public AgentLoopVersion getCurrentVersion() {
        return currentVersion;
    }

    /**
     * 设置 V2 配置
     */
    public void setV2Config(AgentConfig config) {
        this.v2Config = config;
    }

    /**
     * 获取 V2 配置
     */
    public AgentConfig getV2Config() {
        return v2Config;
    }

    /**
     * 启用 V2
     */
    public void enableV2() {
        setVersion(AgentLoopVersion.V2);
        v2Config.setEnabled(true);
    }

    /**
     * 禁用 V2（回滚到 legacy）
     */
    public void disableV2() {
        setVersion(AgentLoopVersion.LEGACY);
        v2Config.setEnabled(false);
    }

    /**
     * 检查是否启用了 V2
     */
    public boolean isV2Enabled() {
        return currentVersion == AgentLoopVersion.V2;
    }
}
