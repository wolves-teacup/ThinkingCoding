package com.thinkingcoding.service.rag;

/**
 * Provider-agnostic vector write adapter.
 */
public interface VectorStoreWriteAdapter {

    WriteResult upsert(VectorWriteRequest request);
}

