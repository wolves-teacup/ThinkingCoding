package com.thinkingcoding.skill;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for built-in skills.
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

