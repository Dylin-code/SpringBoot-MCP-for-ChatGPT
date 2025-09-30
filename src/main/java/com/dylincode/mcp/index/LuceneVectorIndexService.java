package com.dylincode.mcp.index;

import com.dylincode.mcp.model.Chunk;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Lucene 實作向量儲存與查詢
 */
@Component
public class LuceneVectorIndexService implements VectorIndexService {
    private static final String F_ID = "id";
    private static final String F_TITLE = "title";
    private static final String F_URL = "url";
    private static final String F_CONTENT = "content";
    private static final String F_VECTOR = "embedding";
    private static final String F_SUMMARY = "summary"; // TODO 產生摘要

    private final Directory directory;

    public LuceneVectorIndexService(String indexDir) throws IOException {
        this.directory = FSDirectory.open(Path.of(indexDir));
    }

    private IndexWriter writer() throws IOException {
        IndexWriterConfig cfg = new IndexWriterConfig();
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return new IndexWriter(directory, cfg);
    }

    @Override
    public void addAll(List<Chunk> chunks) throws IOException {
        try (IndexWriter w = writer()){
            for (Chunk c : chunks){
                Document d = new Document();
                d.add(new StringField(F_ID, c.id(), Field.Store.YES));
                d.add(new StoredField(F_TITLE, c.title()));
                d.add(new StoredField(F_URL, c.url()));
                d.add(new TextField(F_CONTENT, c.content(), Field.Store.YES));
                d.add(new KnnVectorField(F_VECTOR, c.embedding(), VectorSimilarityFunction.DOT_PRODUCT));
                w.updateDocument(new Term(F_ID, c.id()), d);
            }
            w.commit();
        }
    }

    @Override
    public List<SearchHit> search(float[] queryEmbedding, int k) throws IOException {
        try (DirectoryReader reader = DirectoryReader.open(directory)){
            IndexSearcher searcher = new IndexSearcher(reader);
            Query knn = new KnnVectorQuery(F_VECTOR, queryEmbedding, k);
            TopDocs top = searcher.search(knn, k);
            List<SearchHit> hits = new ArrayList<>();
            for (ScoreDoc sd : top.scoreDocs){
                Document d = searcher.doc(sd.doc);
                hits.add(new SearchHit(
                        d.get(F_ID),
                        d.get(F_TITLE),
                        d.get(F_URL),
                        d.get(F_CONTENT),
                        sd.score
                ));
            }
            return hits;
        }
    }

    @Override
    public List<Chunk> fetchChunks(List<String> chunkIds) throws IOException{
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            for (String id : chunkIds) {
                builder.add(new TermQuery(new Term(F_ID, id)), BooleanClause.Occur.SHOULD);
            }

            Query query = builder.build();
            TopDocs topDocs = searcher.search(query, chunkIds.size());

            List<Chunk> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                // 將 Document 轉換為 Chunk
                Chunk chunk = new Chunk(
                        doc.get(F_ID),
                        doc.get(F_TITLE),
                        doc.get(F_URL),
                        doc.get(F_CONTENT),
                        null // embedding 在這個場景下可能不需要，設為 null 或根據需要從 document 中取得
                );
                results.add(chunk);
            }

            return results;
        }

    }
}
