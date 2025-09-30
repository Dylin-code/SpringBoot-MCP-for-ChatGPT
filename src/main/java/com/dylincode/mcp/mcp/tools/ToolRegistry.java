package com.dylincode.mcp.mcp.tools;

import com.dylincode.mcp.embedding.EmbeddingService;
import com.dylincode.mcp.exception.ClientVisibleException;
import com.dylincode.mcp.index.VectorIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ToolRegistry {
    private final Map<String, Tool> registry = new LinkedHashMap<>();
    private final VectorIndexService store;
    private final EmbeddingService embedding;

    public ToolRegistry(VectorIndexService store, EmbeddingService embedding) {
        this.store = store;
        this.embedding = embedding;
        register(new SearchTool(embedding,store));
        register(new FetchTool(store));
    }

    void register(Tool tool) { registry.put(tool.name(), tool); }

    public List<Map<String, Object>> describe() {
        return registry.values().stream().map(Tool::schema).collect(Collectors.toList());
    }

    public Object call(String name, JsonNode args, ObjectMapper mapper) throws Exception {
        Tool t = registry.get(name);
        if (t == null) throw new ClientVisibleException("Unknown tool: " + name);
        return t.invoke(args, mapper);
    }

}
