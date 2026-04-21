package com.thinkingcoding.skill;

/**
 * Result object for skill execution.
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

