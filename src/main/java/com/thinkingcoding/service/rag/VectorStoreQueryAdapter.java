package com.thinkingcoding.service.rag;

/**
 * Provider-agnostic vector query adapter.
 */
public interface VectorStoreQueryAdapter {

    QueryResult query(VectorQueryRequest request);
}

