package com.thinkingcoding.skill;

public class TestCaptureSkill implements Skill {
    static volatile SkillExecutionContext lastContext;

    @Override
    public String getName() {
        return "capture";
    }

    @Override
    public String getDescription() {
        return "capture skill";
    }

    @Override
    public SkillResult execute(SkillExecutionContext context) {
        lastContext = context;
        return SkillResult.success("ok", null, 1);
    }
}

