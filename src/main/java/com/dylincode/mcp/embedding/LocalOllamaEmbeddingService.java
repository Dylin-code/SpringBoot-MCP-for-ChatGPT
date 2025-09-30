package com.dylincode.mcp.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class LocalOllamaEmbeddingService implements EmbeddingService {
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String model;

    public LocalOllamaEmbeddingService(
            @Value("${app.ollama.baseUrl:http://localhost:11434}") String baseUrl,
            @Value("${app.ollama.model:nomic-embed-text}") String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public float[] embed(String text) throws Exception {
        MediaType JSON = MediaType.parse("application/json");
        String body = String.format("{\"model\":\"%s\",\"prompt\":%s}", model, mapper.writeValueAsString(text));
        Request req = new Request.Builder()
                .url(baseUrl + "/api/embeddings")
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new RuntimeException("Ollama error: " + resp.code());
            JsonNode root = mapper.readTree(resp.body().byteStream());
            JsonNode arr = root.path("embedding");
            float[] v = new float[arr.size()];
            double norm = 0;
            for (int i = 0; i < v.length; i++) { v[i] = (float) arr.get(i).asDouble(); norm += v[i]*v[i]; }
            norm = Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < v.length; i++) v[i] /= (float) norm; // 供 Lucene DOT_PRODUCT
            return v;
        }
    }
}
