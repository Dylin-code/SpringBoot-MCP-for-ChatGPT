package com.dylincode.mcp.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for chunking text into smaller segments. This can be useful for processing large
 * text strings by dividing them into manageable pieces with optional overlap for context.
 */
public class TextChunker {
    public static List<String> split(String text, int chunkSize, int overlap){
        List<String> chunks = new ArrayList<>();
        if (text == null) return chunks;
        text = text.strip();
        if (text.isEmpty()) return chunks;
        int start = 0;
        int end = Math.min(chunkSize, text.length());
        while (start < text.length()){
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
            start = Math.max(0, end - overlap);
            end = Math.min(start + chunkSize, text.length());
        }
        return chunks;
    }
}
