package com.thinkingcoding.skill;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能注册中心，负责管理和提供技能实例。技能通过名称进行注册和获取，名称不区分大小写。
 */
public class SkillRegistry {
    private final Map<String, Skill> skills = new HashMap<>();

    public void register(Skill skill) {
        if (skill != null) {
            skills.put(skill.getName().toLowerCase(), skill);
        }
    }

    public Skill getSkill(String name) {
        if (name == null) {
            return null;
        }
        return skills.get(name.toLowerCase());
    }
}

