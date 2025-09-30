package com.dylincode.mcp.index;

import com.dylincode.mcp.model.Chunk;
import java.io.IOException;
import java.util.List;

/**
 * Defines a service interface for managing and querying a vector-based index for text data.
 * This service allows for adding data, performing similarity searches, and retrieving chunks.
 */
public interface VectorIndexService {
    void addAll(List<Chunk> chunks) throws IOException;

    List<SearchHit> search(float[] queryEmbedding, int k) throws IOException;

    record SearchHit(String chunkId, String title, String url, String content, float score) {}

    List<Chunk> fetchChunks(List<String> chunkIds) throws IOException;
}
