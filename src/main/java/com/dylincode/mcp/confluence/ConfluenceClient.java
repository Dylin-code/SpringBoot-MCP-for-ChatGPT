package com.dylincode.mcp.confluence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Confluence資料源實作
 */
@Component
public class ConfluenceClient {
    private final String baseUrl;
    private final String authHeader;
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public ConfluenceClient(
            @Value("${app.confluence.baseUrl}") String baseUrl,
            @Value("${app.confluence.username}") String username,
            @Value("${app.confluence.apiToken}") String apiToken
    ){
        this.baseUrl = baseUrl;
        if (username != null && !username.isBlank() && apiToken != null && !apiToken.isBlank()){
            String cred = username + ":" + apiToken;
            String b64 = Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8));
            this.authHeader = "Basic " + b64;
        } else {
            this.authHeader = null;
        }
    }

    public static String extractPageIdFromUrl(String url){
        int idx = url.indexOf("/pages/");
        if (idx < 0) return null;
        String rest = url.substring(idx + "/pages/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) return rest;
        return rest.substring(0, slash);
    }

    /**
     * 獲取指定空間的所有頁面
     * @param spaceKey 空間的 key
     * @return 該空間所有頁面的列表
     * @throws IOException 當請求失敗時拋出
     */
    public List<Page> fetchAllPagesInSpace(String spaceKey) throws IOException {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("CONFLUENCE_BASE_URL 未設定");
        }
        if (spaceKey == null || spaceKey.isBlank()) {
            throw new IllegalArgumentException("spaceKey 不能為空");
        }

        List<Page> allPages = new ArrayList<>();
        Integer nextStart = 0;   // 用 API 回傳的 next 連結來更新
        final int limit = 50;

        while (nextStart != null) {
            String url = String.format(
                    "%s/rest/api/space/%s/content?type=page&start=%d&limit=%d&expand=body.storage,version",
                    baseUrl, spaceKey, nextStart, limit
            );

            Request.Builder b = new Request.Builder().url(url).get();
            if (authHeader != null) {
                b.header("Authorization", authHeader);
            }
            b.header("Accept", "application/json");

            try (Response resp = http.newCall(b.build()).execute()) {
                if (!resp.isSuccessful()) {
                    throw new IOException("Confluence 回應非 2xx: " + resp.code() + " - " + resp.message());
                }

                JsonNode root = mapper.readTree(resp.body().byteStream());
                JsonNode pageNode = root.path("page");          // << 分頁資訊在這層
                JsonNode results = pageNode.path("results");

                if (results.isArray()) {
                    for (JsonNode n : results) {
                        String pageId = n.path("id").asText();
                        String title = n.path("title").asText("");
                        String storage = n.path("body").path("storage").path("value").asText("");
                        String webui = n.path("_links").path("webui").asText("");
                        String viewUrl = webui.startsWith("http") ? webui
                                : (webui.startsWith("/") ? baseUrl + webui : baseUrl + "/" + webui);

                        String text = htmlToText(storage);
                        allPages.add(new Page(pageId, title, viewUrl, text));
                    }
                }

                // 依照 page._links.next 來判斷是否還有下一頁
                JsonNode nextNode = pageNode.path("_links").path("next");
                if (nextNode.isMissingNode() || nextNode.isNull()) {
                    nextStart = null; // 沒有下一頁了
                } else {
                    // 解析 next 連結的 start 參數
                    String nextRel = nextNode.asText(); // 通常是相對路徑，例如 /rest/api/space/...&start=50&limit=50
                    String nextUrl = nextRel.startsWith("http") ? nextRel
                            : (nextRel.startsWith("/") ? baseUrl + nextRel : baseUrl + "/" + nextRel);

                    HttpUrl parsed = HttpUrl.parse(nextUrl);
                    String startParam = parsed != null ? parsed.queryParameter("start") : null;
                    if (startParam != null) {
                        nextStart = Integer.parseInt(startParam);
                    } else {
                        // 保底：若沒有 start 參數，就用目前回傳的 start+limit 推估
                        int curStart = pageNode.path("start").asInt(0);
                        int curLimit = pageNode.path("limit").asInt(limit);
                        nextStart = curStart + curLimit;
                    }
                }
            }
        }

        return allPages;
    }



    public Page fetchPage(String pageId) throws IOException {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalStateException("CONFLUENCE_BASE_URL 未設定");
        String url = baseUrl + "/rest/api/content/" + pageId + "?expand=body.storage,version";
        Request.Builder b = new Request.Builder().url(url).get();
        if (authHeader != null) b.header("Authorization", authHeader);
        b.header("Accept", "application/json");

        try (Response resp = http.newCall(b.build()).execute()){
            if (!resp.isSuccessful()) throw new IOException("Confluence 回應非 2xx: " + resp.code());
            JsonNode root = mapper.readTree(resp.body().byteStream());
            String title = root.path("title").asText("");
            String storage = root.path("body").path("storage").path("value").asText("");
            String viewUrl = baseUrl + root.path("_links").path("webui").asText();
            String text = htmlToText(storage);
            return new Page(pageId, title, viewUrl, text);
        }
    }

    private static String htmlToText(String html){
        return Jsoup.parse(html).text();
    }

    public record Page(String id, String title, String url, String text){}
}
