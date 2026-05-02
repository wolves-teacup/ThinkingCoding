package com.thinkingcoding.skill;

/**
 * 技能执行上下文，包含技能执行所需的基本信息，如源代码路径、测试文件路径、模型选择、最大重试次数和技能上下文等。
 */
public class SkillExecutionContext {
    private final String sourcePath;
    private final String testPath;
    private final String model;
    private final int maxRetries;
    private final String skillContext;

    public SkillExecutionContext(String sourcePath, String testPath, String model, int maxRetries) {
        this(sourcePath, testPath, model, maxRetries, null);
    }

    public SkillExecutionContext(String sourcePath, String testPath, String model, int maxRetries, String skillContext) {
        this.sourcePath = sourcePath;
        this.testPath = testPath;
        this.model = model;
        this.maxRetries = maxRetries;
        this.skillContext = skillContext;
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

    public String getSkillContext() {
        return skillContext;
    }
}

