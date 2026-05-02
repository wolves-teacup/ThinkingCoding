package com.thinkingcoding.skill;

import com.thinkingcoding.config.AppConfig;
import com.thinkingcoding.tools.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LazySkillToolAdapterTest {

    @Test
    void loadsFullContextOnExecute() {
        AppConfig appConfig = new AppConfig();
        ToolRegistry toolRegistry = new ToolRegistry(appConfig);

        AppConfig.SkillConfig skillConfig = new AppConfig.SkillConfig();
        skillConfig.setName("capture");
        skillConfig.setClassName("com.thinkingcoding.skill.TestCaptureSkill");
        skillConfig.setFullContext("FULL_CONTEXT");
        skillConfig.setEnabled(true);

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        skillConfig.setInputSchema(schema);

        LazySkillToolAdapter adapter = new LazySkillToolAdapter(skillConfig, appConfig, null, toolRegistry);
        adapter.execute("{\"source\":\"Demo.java\"}");

        SkillExecutionContext captured = TestCaptureSkill.lastContext;
        assertNotNull(captured);
        assertEquals("FULL_CONTEXT", captured.getSkillContext());
        assertEquals("Demo.java", captured.getSourcePath());
    }
}

