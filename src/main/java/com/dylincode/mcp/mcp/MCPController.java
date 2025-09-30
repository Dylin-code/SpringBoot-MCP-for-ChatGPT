package com.dylincode.mcp.mcp;

import com.dylincode.mcp.embedding.EmbeddingService;
import com.dylincode.mcp.exception.ClientVisibleException;
import com.dylincode.mcp.index.VectorIndexService;

import com.dylincode.mcp.mcp.tools.ToolRegistry;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;


/**
 * Minimal Spring Boot JSON‑RPC 2.0 endpoint that speaks MCP over HTTP.
 * <p>
 * Notes:
 * - MCP uses JSON‑RPC 2.0 messages. This controller handles both single and batch requests.
 * - Implemented methods:
 * "initialize", "tools/list", "tools/call",
 * "resources/list", "resources/read".
 * - Replace InMemoryVectorStore with your Lucene/BGE‑M3 implementation.
 * <p>
 * Usage:
 * POST /mcp with a JSON‑RPC request body.
 * <p>
 * Example requests (single):
 * {"jsonrpc":"2.0","id":"1","method":"initialize","params":{"client":{"name":"test","version":"0.1.0"}}}
 * {"jsonrpc":"2.0","id":"2","method":"tools/list"}
 * {"jsonrpc":"2.0","id":"3","method":"tools/call","params":{"name":"search","arguments":{"query":"payment rollback","top_k":3}}}
 * {"jsonrpc":"2.0","id":"4","method":"tools/call","params":{"name":"fetch","arguments":{"ids":["a_3","a_4"]}}}
 */
@Slf4j
@RestController
@RequestMapping(path = {"/mcp", "/"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class MCPController {

    private final ObjectMapper mapper;
    private final ToolRegistry tools;
    private final EmbeddingService embedding;

    public MCPController(ObjectMapper mapper, VectorIndexService store, EmbeddingService embedding) {
        this.mapper = mapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.tools = new ToolRegistry(store, embedding); // swap in your Lucene impl
        this.embedding = embedding;
    }

    // ---- JSON‑RPC entrypoint -------------------------------------------------

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP");
    }

    @PostMapping
    public Object handle(@RequestBody JsonNode body) {
        if (body.isArray()) {
            List<JsonNode> arr = mapper.convertValue(body, new TypeReference<>() {
            });
            List<JsonRpcResponse> out = new ArrayList<>();
            for (JsonNode node : arr) out.add(processOne(mapper.convertValue(node, JsonRpcRequest.class)));
            return out;
        }
        JsonRpcRequest req = mapper.convertValue(body, JsonRpcRequest.class);
        if ("notifications/initialized".equals(req.method)) {
            log.info("Client initialized at {}", Instant.now());
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }
        return processOne(req);
    }

    private JsonRpcResponse processOne(JsonRpcRequest req) {
        try {
            if (!"2.0".equals(req.jsonrpc)) {
                return JsonRpcResponse.error(req.id, JsonRpcError.invalidRequest("jsonrpc must be '2.0'"));
            }
            return switch (req.method) {
                case "initialize" -> initialize(req);
                case "tools/list" -> toolsList(req);
                case "tools/call" -> toolsCall(req);
//                case "resources/list" -> resourcesList(req);
//                case "resources/read" -> resourcesRead(req);
                default -> JsonRpcResponse.error(req.id, JsonRpcError.methodNotFound(req.method));
            };
        } catch (ClientVisibleException e) {
            return JsonRpcResponse.error(req.id, new JsonRpcError(-32001, e.getMessage(), e.details));
        } catch (Exception e) {
            return JsonRpcResponse.error(req.id, JsonRpcError.internalError(e));
        }
    }

    // ---- MCP methods ---------------------------------------------------------

    private JsonRpcResponse initialize(JsonRpcRequest req) {
        Map<String, Object> params = req.paramsAs(mapper, new TypeReference<>() {
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverInfo", Map.of(
                "name", "fcom-mcp-server",
                "version", "1.0.0",
                "title", "Fcom MCP Server"
        ));
        // Advertise capabilities this server supports
        result.put("capabilities", Map.of(
                "tools", Map.of("listChanged", true),
                "logging", Map.of()
        ));
        result.put("protocolVersion", "2025-03-26"); // JSON‑RPC version echo
        result.put("instructions", "Search on fcom Confluence");
        log.info("init call at {}", LocalDateTime.now());
        return JsonRpcResponse.ok(req.id, result);
    }

    private JsonRpcResponse toolsList(JsonRpcRequest req) {
        Map<String, Object> result = Map.of(
                "tools", tools.describe()
        );
        return JsonRpcResponse.ok(req.id, result);
    }

    private JsonRpcResponse toolsCall(JsonRpcRequest req) throws Exception {
        log.info("tool call req: {}", mapper.writeValueAsString(req));
        Map<String, Object> p = req.paramsAs(mapper, new TypeReference<>() {
        });
        String name = (String) p.get("name");
        if (!StringUtils.hasText(name)) throw new ClientVisibleException("Missing tool name");
        JsonNode args = mapper.valueToTree(p.get("arguments"));
        Object out = tools.call(name, args, mapper);
        log.info("tool call res: {}", out);
        return JsonRpcResponse.ok(req.id, Map.of("content", Collections.singletonList(out)));
    }

//    private JsonRpcResponse resourcesList(JsonRpcRequest req) {
//        // Optional: expose fetchable resource URIs (documents, datasets…)
//        List<Map<String, Object>> resources = tools.listResources();
//        return JsonRpcResponse.ok(req.id, Map.of("resources", resources));
//    }

//    private JsonRpcResponse resourcesRead(JsonRpcRequest req) {
//        Map<String, Object> p = req.paramsAs(mapper, new TypeReference<>() {});
//        String uri = (String) p.get("uri");
//        if (!StringUtils.hasText(uri)) throw new ClientVisibleException("Missing 'uri'");
//        Map<String, Object> resource = tools.readResource(uri);
//        return JsonRpcResponse.ok(req.id, Map.of("contents", List.of(resource)));
//    }

    // ---- Tool registry & simple vector store --------------------------------


    // ---- JSON‑RPC plumbing ---------------------------------------------------

    static class JsonRpcRequest {
        public String jsonrpc = "2.0";
        public String method;
        public JsonNode params; // object or array or null
        public Object id;       // string or number; here we normalize to string/null

        <T> T paramsAs(ObjectMapper m, TypeReference<T> type) {
            if (params == null || params.isNull()) return m.convertValue(Map.of(), type);
            return m.convertValue(params, type);
        }
    }

    static class JsonRpcResponse {
        public String jsonrpc = "2.0";
        public Object result;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public JsonRpcError error;
        public Object id;

        static JsonRpcResponse ok(Object id, Object result) {
            JsonRpcResponse r = new JsonRpcResponse();
            r.id = id;
            r.result = result;
            return r;
        }

        static JsonRpcResponse error(Object id, JsonRpcError e) {
            JsonRpcResponse r = new JsonRpcResponse();
            r.id = id;
            r.error = e;
            return r;
        }
    }

    static class JsonRpcError {
        public int code;
        public String message;
        public Object data;

        JsonRpcError() {
        }

        JsonRpcError(int code, String message, Object data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        static JsonRpcError methodNotFound(String m) {
            return new JsonRpcError(-32601, "Method not found: " + m, null);
        }

        static JsonRpcError invalidRequest(String msg) {
            return new JsonRpcError(-32600, msg, null);
        }

        static JsonRpcError internalError(Exception e) {
            return new JsonRpcError(-32603, "Internal error", Map.of("exception", e.getClass().getSimpleName(), "message", e.getMessage()));
        }
    }
}

