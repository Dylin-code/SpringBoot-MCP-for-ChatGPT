package com.dylincode.mcp.web;

import com.dylincode.mcp.config.ConfluenceConfig;
import com.dylincode.mcp.config.WhiteListConfig;
import com.dylincode.mcp.confluence.ConfluenceClient;
import com.dylincode.mcp.embedding.EmbeddingService;
import com.dylincode.mcp.index.VectorIndexService;
import com.dylincode.mcp.model.Chunk;
import com.dylincode.mcp.util.TextChunker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 數據攝取控制器 - 負責管理外部數據源到向量索引系統的數據攝取流程。
 *
 * <p>此控制器提供了完整的數據攝取解決方案，主要功能包括：</p>
 * <ul>
 *   <li><strong>空間級攝取</strong> - 批量攝取指定 Confluence 空間的所有頁面內容</li>
 *   <li><strong>頁面級攝取</strong> - 精確攝取指定頁面 ID 或 URL 的內容</li>
 *   <li><strong>智能分塊</strong> - 將長文本分割為適合向量化的小塊</li>
 *   <li><strong>向量化處理</strong> - 為每個文本塊生成語意嵌入向量</li>
 *   <li><strong>索引存儲</strong> - 將處理後的向量數據存入檢索系統</li>
 * </ul>
 *
 * <p><strong>核心處理流程：</strong></p>
 * <ol>
 *   <li>從 Confluence 獲取原始頁面內容</li>
 *   <li>使用 TextChunker 將內容分割為重疊的文本塊</li>
 *   <li>通過 EmbeddingService 為每個塊生成向量表示</li>
 *   <li>將向量化的塊存儲到 VectorIndexService 中</li>
 *   <li>提供進度追蹤和錯誤處理機制</li>
 * </ol>
 *
 * <p><strong>依賴服務說明：</strong></p>
 * <ul>
 *   <li><strong>ConfluenceClient</strong> - 處理與 Confluence API 的交互</li>
 *   <li><strong>EmbeddingService</strong> - 提供文本向量化服務</li>
 *   <li><strong>VectorIndexService</strong> - 管理向量索引的存儲和檢索</li>
 *   <li><strong>ConfluenceConfig</strong> - 配置需要同步的 Confluence 空間</li>
 * </ul>
 *
 * <p><strong>線程安全設計：</strong></p>
 * <ul>
 *   <li>使用虛擬線程處理耗時的攝取操作，避免阻塞主線程</li>
 *   <li>通過 ConcurrentHashMap 實現空間級的攝取鎖定機制</li>
 *   <li>防止同一空間的並發攝取導致數據重複或競爭條件</li>
 * </ul>
 *
 * @see ConfluenceClient 用於 Confluence 數據獲取
 * @see EmbeddingService 用於文本向量化
 * @see VectorIndexService 用於向量索引管理
 */

@Slf4j
@RestController
@RequestMapping("/ingest")
public class IngestController {
    private final ConfluenceClient confluence;
    private final EmbeddingService embedding;
    private final VectorIndexService indexService;
    @Autowired
    private ConfluenceConfig spaces;

    private static final int CHUNK_SIZE = 800;
    private static final int OVERLAP = 120;

    private final ConcurrentHashMap<String, Boolean> ingestLock = new ConcurrentHashMap<>();

    public IngestController(ConfluenceClient confluence, EmbeddingService embedding, VectorIndexService indexService) {
        this.confluence = confluence;
        this.embedding = embedding;
        this.indexService = indexService;
    }

    public record IngestRequest(List<String> pageIds, List<String> pageUrls, Integer chunkSize, Integer chunkOverlap,
                                String spaceKey) {
    }

//    @Scheduled(cron = "0 0 8,21 * * ?")
    public void updateIndexSchedule(){
        spaces.getKeys().forEach(key -> {
            IngestRequest req = new IngestRequest(null, null, CHUNK_SIZE, OVERLAP, key);
            log.info("updateIndexSchedule ingest space {}", key);
            ingestAllSpace(req);
            log.info("updateIndexSchedule ingest end space {}", key);
        });
    }

    @PostMapping("/space")
    public ResponseEntity<?> ingestAllSpace(@RequestBody IngestRequest request) {
        Thread.startVirtualThread(() -> {
            boolean lock = ingestLock.putIfAbsent(request.spaceKey(), true) == null;
            if (!lock) {
                return;
            }
            try {
                var pages = confluence.fetchAllPagesInSpace(request.spaceKey());
                log.info("ingest space {}, pageSize:{}", request.spaceKey, pages.size());
                List<Chunk> all = new ArrayList<>();
                int pi = 0;
                for (var page : pages) {
                    var pieces = TextChunker.split(page.text(), CHUNK_SIZE, OVERLAP);
                    int i = 0;
                    for (String p : pieces) {
                        float[] vec = embedding.embed(p);
                        String chunkId = page.id() + "_" + (i++);
                        all.add(new Chunk(chunkId, page.title(), page.url(), p, vec));
                    }
                    if (pi % 10 == 0) {
                        BigInteger percentage = BigDecimal.valueOf(pi).divide(BigDecimal.valueOf(pages.size()), 2, RoundingMode.DOWN).multiply(BigDecimal.valueOf(100)).toBigInteger();
                        log.info("ingest space {} process {}%", request.spaceKey(), percentage);
                    }
                    pi++;
                }
                indexService.addAll(all);
                ingestLock.remove(request.spaceKey());
            } catch (Exception e) {
                log.error("ingestAllSpace error", e);
                ingestLock.remove(request.spaceKey());
            }

        });
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<?> ingest(@RequestBody IngestRequest req) throws Exception {
        List<String> ids = new ArrayList<>();
        if (req.pageIds() != null) ids.addAll(req.pageIds());
        if (req.pageUrls() != null) {
            for (String url : req.pageUrls()) {
                String id = ConfluenceClient.extractPageIdFromUrl(url);
                if (id != null) ids.add(id);
            }
        }
        if (ids.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "請提供 pageIds 或 pageUrls"));

        List<Chunk> all = new ArrayList<>();
        for (String id : ids) {
            var page = confluence.fetchPage(id);
            var pieces = TextChunker.split(page.text(), CHUNK_SIZE, OVERLAP);
            int i = 0;
            for (String p : pieces) {
                float[] vec = embedding.embed(p);
                String chunkId = page.id() + "_" + (i++);
                all.add(new Chunk(chunkId, page.title(), page.url(), p, vec));
            }
        }
        indexService.addAll(all);
        return ResponseEntity.ok(Map.of("indexedChunks", all.size()));
    }
}
