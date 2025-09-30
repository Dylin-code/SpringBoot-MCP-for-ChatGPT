package com.dylincode.mcp.web;

import com.dylincode.mcp.embedding.EmbeddingService;
import com.dylincode.mcp.index.VectorIndexService;
import okhttp3.*;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/ask")
public class AskController {
    private final EmbeddingService embedding;
    private final VectorIndexService indexService;
    private final OkHttpClient http = new OkHttpClient();

    private final String openaiKey;
    private final String openaiModel;

    public AskController(
            EmbeddingService embedding, VectorIndexService indexService,
            @Value("${app.openai.apiKey:}") String openaiKey,
            @Value("${app.openai.chatModel:gpt-5}") String openaiModel
    ){
        this.embedding = embedding;
        this.indexService = indexService;
        this.openaiKey = openaiKey;
        this.openaiModel = openaiModel;
    }

    public record AskRequest(String q, Integer k) {}
    public record AskResponse(String answer) {}

    @PostMapping
    public ResponseEntity<?> ask(@org.springframework.web.bind.annotation.RequestBody AskRequest req) throws Exception {
        int k = (req.k()==null) ? 5 : Math.max(1, req.k());
        float[] qvec = embedding.embed(req.q());
        var hits = indexService.search(qvec, k);

        // 組 context（限制長度，避免超過 token）
        String context = hits.stream()
                .map(h -> String.format(
                        "Title: %s\nURL: %s\nExcerpt:\n%s\n---",
                        h.title(), h.url(), truncate(h.content(), 1200)))
                .collect(Collectors.joining("\n"));

        String system = """
      你是內部知識助理。僅依據給定「資料片段」回答，
      若資料不足請直接說不知道；回答末尾列出來源 URL 清單。
      """;
        String user = """
      問題：
      %s

      參考資料（多來源片段）：
      %s
      """.formatted(req.q(), context);

        String answer = callOpenAI(system, user);



        return ResponseEntity.ok(new AskResponse(answer));
    }

    private static String truncate(String s, int max){
        if (s==null) return "";
        return s.length()<=max ? s : s.substring(0, max) + " …";
    }

    private String callOpenAI(String system, String user) throws Exception {
        MediaType JSON = MediaType.parse("application/json");
        String body = """
      {"model":"%s","messages":[
        {"role":"system","content":%s},
        {"role":"user","content":%s}
      ]}
      """.formatted(openaiModel, json(system), json(user));
        Request req = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization","Bearer " + openaiKey)
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response resp = http.newCall(req).execute()){
            var root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(resp.body().byteStream());
            return root.path("choices").get(0).path("message").path("content").asText();
        }
    }

    private static String json(String s) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(s);
    }
}
