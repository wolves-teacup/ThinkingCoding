package com.thinkingcoding.service.rag;

import java.util.List;
import java.util.Map;

/**
 * A normalized vector write request sent to cloud vector stores.
 */
public class VectorWriteRequest {

    private final String id;
    private final List<Float> vector;
    private final String text;
    private final Map<String, String> metadata;

    public VectorWriteRequest(String id, List<Float> vector, String text, Map<String, String> metadata) {
        this.id = id;
        this.vector = vector;
        this.text = text;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public List<Float> getVector() {
        return vector;
    }

    public String getText() {
        return text;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}

