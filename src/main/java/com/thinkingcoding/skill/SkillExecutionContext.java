package com.thinkingcoding.skill;

/**
 * Runtime parameters for executing a skill.
 */
public class SkillExecutionContext {
    private final String sourcePath;
    private final String testPath;
    private final String model;
    private final int maxRetries;

    public SkillExecutionContext(String sourcePath, String testPath, String model, int maxRetries) {
        this.sourcePath = sourcePath;
        this.testPath = testPath;
        this.model = model;
        this.maxRetries = maxRetries;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getTestPath() {
        return testPath;
    }

    public String getModel() {
        return model;
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}

