package com.thinkingcoding.service.rag;

/**
 * Write outcome for cloud vector upsert.
 */
public class WriteResult {

    private final boolean success;
    private final int statusCode;
    private final String message;

    private WriteResult(boolean success, int statusCode, String message) {
        this.success = success;
        this.statusCode = statusCode;
        this.message = message;
    }

    public static WriteResult success(int statusCode, String message) {
        return new WriteResult(true, statusCode, message);
    }

    public static WriteResult failure(int statusCode, String message) {
        return new WriteResult(false, statusCode, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }
}

