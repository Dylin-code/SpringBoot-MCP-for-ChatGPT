package com.dylincode.mcp.model;

public record Chunk(
        String id,
        String title,
        String url,
        String content,
        float[] embedding
) {}
