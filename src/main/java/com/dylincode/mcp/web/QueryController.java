package com.dylincode.mcp.web;

import com.dylincode.mcp.embedding.EmbeddingService;
import com.dylincode.mcp.index.VectorIndexService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RAG search test
 */
@RestController
@RequestMapping("/query")
public class QueryController {
    private final EmbeddingService embedding;
    private final VectorIndexService indexService;

    public QueryController(EmbeddingService embedding, VectorIndexService indexService){
        this.embedding = embedding;
        this.indexService = indexService;
    }

    @GetMapping
    public ResponseEntity<?> query(@RequestParam("q") String q, @RequestParam(value = "k", required = false) Integer k) throws Exception {
        int topK = (k == null) ? 5 : Math.max(1, k);
        float[] vec = embedding.embed(q);
        List<VectorIndexService.SearchHit> hits = indexService.search(vec, topK);
        return ResponseEntity.ok(Map.of("results", hits));
    }
}
