package com.thinkingcoding.service.rag;

/**
 * Disabled cloud writer implementation.
 */
public class NoopVectorStoreWriteAdapter implements VectorStoreWriteAdapter {

    @Override
    public WriteResult upsert(VectorWriteRequest request) {
        return WriteResult.success(204, "Cloud vector write disabled");
    }
}

