package com.thinkingcoding.service.rag;

import java.util.Map;

/**
 * Single vector query match from cloud store.
 */
public class QueryMatch {

    private final String text;
    private final Map<String, String> metadata;
    private final double score;

    public QueryMatch(String text, Map<String, String> metadata, double score) {
        this.text = text;
        this.metadata = metadata;
        this.score = score;
    }

    public String getText() {
        return text;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public double getScore() {
        return score;
    }
}

