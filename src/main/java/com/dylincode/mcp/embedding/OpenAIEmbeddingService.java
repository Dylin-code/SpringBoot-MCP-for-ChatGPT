package com.dylincode.mcp.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpenAIEmbeddingService implements EmbeddingService {
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public OpenAIEmbeddingService(
            @Value("${app.openai.apiKey}") String apiKey,
            @Value("${app.openai.embeddingsModel:text-embedding-3-small}") String model
    ){
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public float[] embed(String text) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY 未設定");
        }
        MediaType json = MediaType.parse("application/json");
        String body = String.format("{\n  \"model\": \"%s\",\n  \"input\": %s\n}", model, mapper.writeValueAsString(text));
        Request req = new Request.Builder()
                .url("https://api.openai.com/v1/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, json))
                .build();
        try (Response resp = http.newCall(req).execute()){
            if (!resp.isSuccessful()) throw new IOException("OpenAI 回應非 2xx: " + resp.code() + " - " + resp.message());
            JsonNode root = mapper.readTree(resp.body().byteStream());
            JsonNode vec = root.path("data").get(0).path("embedding");
            List<Float> floats = new ArrayList<>();
            for (JsonNode n : vec) floats.add((float)n.asDouble());
            float[] arr = new float[floats.size()];
            double norm = 0.0;
            for (int i = 0; i < arr.length; i++){ arr[i] = floats.get(i); norm += arr[i]*arr[i]; }
            norm = Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < arr.length; i++) arr[i] /= (float)norm;
            return arr;
        }
    }
}
