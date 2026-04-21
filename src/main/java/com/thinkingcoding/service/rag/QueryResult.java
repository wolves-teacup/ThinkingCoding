package com.thinkingcoding.service.rag;

import java.util.Collections;
import java.util.List;

/**
 * Query response wrapper for cloud vector search.
 */
public class QueryResult {

    private final boolean success;
    private final int statusCode;
    private final String message;
    private final List<QueryMatch> matches;

    private QueryResult(boolean success, int statusCode, String message, List<QueryMatch> matches) {
        this.success = success;
        this.statusCode = statusCode;
        this.message = message;
        this.matches = matches;
    }

    public static QueryResult success(int statusCode, String message, List<QueryMatch> matches) {
        return new QueryResult(true, statusCode, message, matches == null ? Collections.emptyList() : matches);
    }

    public static QueryResult failure(int statusCode, String message) {
        return new QueryResult(false, statusCode, message, Collections.emptyList());
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

    public List<QueryMatch> getMatches() {
        return matches;
    }
}

