package com.thinkingcoding.service.rag;

import java.util.List;

/**
 * Query request to cloud vector search.
 */
public class VectorQueryRequest {

    private final List<Float> vector;
    private final int topK;
    private final double minScore;

    public VectorQueryRequest(List<Float> vector, int topK, double minScore) {
        this.vector = vector;
        this.topK = topK;
        this.minScore = minScore;
    }

    public List<Float> getVector() {
        return vector;
    }

    public int getTopK() {
        return topK;
    }

    public double getMinScore() {
        return minScore;
    }
}

