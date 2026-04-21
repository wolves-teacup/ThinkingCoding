package com.thinkingcoding.skill;

/**
 * Skill contract for executable workflow modules.
 */
public interface Skill {
    String getName();

    SkillResult execute(SkillExecutionContext context);
}

