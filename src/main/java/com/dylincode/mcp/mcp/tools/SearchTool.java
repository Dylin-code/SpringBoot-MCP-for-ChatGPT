package com.dylincode.mcp.mcp.tools;

import com.dylincode.mcp.embedding.EmbeddingService;
import com.dylincode.mcp.exception.ClientVisibleException;
import com.dylincode.mcp.index.VectorIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class SearchTool implements Tool {

    private final VectorIndexService store;
    private final EmbeddingService embedding;

    SearchTool(EmbeddingService embedding, VectorIndexService store) {
        this.embedding = embedding;
        this.store = store;
    }

    public String name() { return "search"; }
    public Map<String, Object> schema() {
        return Map.of(
                "name", name(),
                "description", "Vector keyword hybrid search that returns chunk ids and lightweight previews.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string"),
                                "top_k", Map.of("type", "integer", "default", 5)
                        ),
                        "required", List.of("query")
                )
        );
    }
    public Object invoke(JsonNode arguments, ObjectMapper mapper) throws Exception {
        String query = Optional.ofNullable(arguments.get("query")).map(JsonNode::asText).orElse("");
        int topK = Optional.ofNullable(arguments.get("top_k")).map(JsonNode::asInt).orElse(5);
        if (!StringUtils.hasText(query)) throw new ClientVisibleException("'query' is required");
        float[] vec = embedding.embed(query);
        List<VectorIndexService.SearchHit> hits = store.search(vec, topK);
        List<Map<String, Object>> items = new ArrayList<>();
        for (VectorIndexService.SearchHit h : hits) {
            items.add(Map.of(
                    "id", h.chunkId(),
                    "title", h.title(),
                    "score", h.score(),
                    "url", h.url()
            ));
        }
        return Map.of("type", "text", "text", mapper.writeValueAsString(Map.of("results",items)));
    }
}
