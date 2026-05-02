package com.thinkingcoding.skill;

import com.thinkingcoding.config.AppConfig;
import com.thinkingcoding.service.AIService;
import com.thinkingcoding.skill.autotest.AutoTestSkill;
import com.thinkingcoding.tools.ToolRegistry;

/**
 * 技能工厂类，负责根据配置创建技能实例。通过反射机制动态加载技能类，支持自动测试技能的特殊处理。
 */
public final class SkillFactory {
    private SkillFactory() {
    }

    public static Skill createSkillInstance(AppConfig.SkillConfig skillConfig, AppConfig appConfig, AIService aiService, ToolRegistry toolRegistry) {
        if (skillConfig == null) {
            return null;
        }
        String className = skillConfig.getClassName();
        if (className == null || className.isBlank()) {
            return null;
        }

        try {
            if (className.contains("AutoTestSkill")) {
                return new AutoTestSkill(aiService, toolRegistry, appConfig);
            }

            Class<?> clazz = Class.forName(className);
            return (Skill) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("创建 Skill 实例失败 [" + className + "]: " + e.getMessage());
            return null;
        }
    }
}

