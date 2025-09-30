package com.dylincode.mcp.mcp.tools;

import com.dylincode.mcp.index.VectorIndexService;
import com.dylincode.mcp.model.Chunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class FetchTool implements Tool {
    private final VectorIndexService store;
    FetchTool(VectorIndexService store) { this.store = store; }
    public String name() { return "fetch"; }
    public Map<String, Object> schema() {
        return Map.of(
                "name", name(),
                "description", "Fetch full text for chunk ids.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "id", Map.of("type", "string", "items", Map.of("type", "string")),
                                "ids", Map.of("type", "array", "items", Map.of("type", "string"))
                        ),
                        "required", List.of("id")
                )
        );
    }
    public Object invoke(JsonNode arguments, ObjectMapper mapper) throws IOException {
        List<String> ids = new ArrayList<>();
        ids.add(arguments.get("id").asText());
        Optional.ofNullable(arguments.get("ids")).ifPresent(node -> node.forEach(id -> ids.add(id.asText())));
        List<Chunk> chunks = store.fetchChunks(ids);
        List<Map<String, String>> items = chunks.stream().map(c -> Map.of(
                "id", c.id(),
                "title", c.title(),
                "url", c.url(),
                "text", c.content()
        )).toList();
        return Map.of("type", "text", "text", mapper.writeValueAsString(Map.of("results",items)));
    }
}
