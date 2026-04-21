package com.thinkingcoding.service.rag;

/**
 * Disabled cloud query implementation.
 */
public class NoopVectorStoreQueryAdapter implements VectorStoreQueryAdapter {

    @Override
    public QueryResult query(VectorQueryRequest request) {
        return QueryResult.success(204, "Cloud vector query disabled", java.util.Collections.emptyList());
    }
}

