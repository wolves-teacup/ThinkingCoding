package com.thinkingcoding.skill;

/**
 * Skill contract for executable workflow modules.
 */
public interface Skill {
    String getName();

    /**
     * Get skill description for AI to understand when to use this skill.
     * This should be a concise description that helps AI decide when to invoke the skill.
     */
    String getDescription();

    /**
     * Get input schema for the skill, used by AI to understand required parameters.
     * Returns null if the skill doesn't require structured input.
     */
    default Object getInputSchema() {
        return null;
    }

    SkillResult execute(SkillExecutionContext context);
}

