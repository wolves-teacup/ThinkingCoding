package com.thinkingcoding.skill;

/**
 * 技能执行结果封装类，包含执行成功与否、相关消息、测试文件路径、最后错误日志和尝试次数等信息。
 */
public class SkillResult {
    private final boolean success;
    private final String message;
    private final String testFilePath;
    private final String lastErrorLog;
    private final int attempts;

    private SkillResult(boolean success, String message, String testFilePath, String lastErrorLog, int attempts) {
        this.success = success;
        this.message = message;
        this.testFilePath = testFilePath;
        this.lastErrorLog = lastErrorLog;
        this.attempts = attempts;
    }

    public static SkillResult success(String message, String testFilePath, int attempts) {
        return new SkillResult(true, message, testFilePath, null, attempts);
    }

    public static SkillResult failure(String message, String testFilePath, String lastErrorLog, int attempts) {
        return new SkillResult(false, message, testFilePath, lastErrorLog, attempts);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getTestFilePath() {
        return testFilePath;
    }

    public String getLastErrorLog() {
        return lastErrorLog;
    }

    public int getAttempts() {
        return attempts;
    }
}

