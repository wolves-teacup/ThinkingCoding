package com.thinkingcoding.service;

import com.thinkingcoding.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextManagerTest {

    private ContextManager createManager(int maxTokens, int maxContextTokens) {
        AppConfig appConfig = new AppConfig();
        AppConfig.ModelConfig modelConfig = new AppConfig.ModelConfig();
        modelConfig.setName("test-model");
        modelConfig.setBaseURL("http://localhost");
        modelConfig.setApiKey("test-key");
        modelConfig.setMaxTokens(maxTokens);
        modelConfig.setMaxContextTokens(maxContextTokens);

        Map<String, AppConfig.ModelConfig> models = new HashMap<>();
        models.put("test", modelConfig);
        appConfig.setModels(models);
        appConfig.setDefaultModel("test");

        return new ContextManager(appConfig);
    }

    @Test
    void shouldCompressWhenHistoryExceedsThreshold() {
        ContextManager manager = createManager(100, 300);
        manager.recordTokenUsage(200, 60, 260);
        assertTrue(manager.shouldCompressHistory());
    }

    @Test
    void shouldNotCompressWhenUsageMissing() {
        ContextManager manager = createManager(100, 300);
        assertFalse(manager.shouldCompressHistory());
    }

    @Test
    void resetTokenUsageClearsHistoryTokens() {
        ContextManager manager = createManager(100, 300);
        manager.recordTokenUsage(200, 60, 260);
        manager.resetTokenUsage();
        assertFalse(manager.shouldCompressHistory());
    }
}

