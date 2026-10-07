package com.hongjie.pms.AI.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Qdrant 的知识库向量存储（HTTP REST，无需额外 SDK 依赖）。
 *
 * <p>point id 用 docId 的确定性 UUID，保证重复索引幂等。维度取自当前嵌入模型（首次探测一次）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantVectorStore {

    @Value("${qdrant.host:127.0.0.1}")
    private String host;

    @Value("${qdrant.port:6333}")
    private int port;

    @Value("${qdrant.collection:ai_knowledge}")
    private String collection;

    @Value("${qdrant.timeout-ms:5000}")
    private int timeoutMs;

    private final EmbeddingModel embeddingModel;

    private volatile boolean collectionReady = false;

    /** 检索命中：docId + 相似度分数 */
    public record SearchHit(String docId, float score) {
    }

    private String baseUrl() {
        return "http://" + host + ":" + port;
    }

    /** 确保 collection 存在，且维度与当前嵌入模型一致（不一致则删除重建，支持换模型） */
    public synchronized void ensureCollection() {
        if (collectionReady) {
            return;
        }
        try {
            int dims = embeddingModel.embed("dim-probe").content().dimension();
            HttpResponse<String> head = http("GET", "/collections/" + collection, null);
            if (head.statusCode() == 200) {
                int existing = parseVectorSize(head.body());
                if (existing == dims) {
                    log.info("[Qdrant] collection '{}' 已存在，dims={}", collection, dims);
                    collectionReady = true;
                    return;
                }
                log.warn("[Qdrant] collection '{}' 维度不匹配（现有{} vs 模型{}），删除重建", collection, existing, dims);
                http("DELETE", "/collections/" + collection, null);
            }
            String body = "{\"vectors\":{\"size\":" + dims + ",\"distance\":\"Cosine\"}}";
            HttpResponse<String> put = http("PUT", "/collections/" + collection, body);
            log.info("[Qdrant] 创建 collection '{}' dims={} → HTTP {}", collection, dims, put.statusCode());
            collectionReady = put.statusCode() == 200;
        } catch (Exception e) {
            log.error("[Qdrant] ensureCollection 失败: {}", e.getMessage(), e);
        }
    }

    /** 从 collection 信息 JSON 里取向量维度，取不到返回 -1 */
    private int parseVectorSize(String body) {
        try {
            JSONObject result = JSON.parseObject(body).getJSONObject("result");
            JSONObject params = result.getJSONObject("config").getJSONObject("params");
            Object vectors = params.get("vectors");
            if (vectors instanceof JSONObject v) {
                return v.getIntValue("size");
            }
            return result.getJSONObject("config").getJSONObject("params").getIntValue("size");
        } catch (Exception e) {
            return -1;
        }
    }

    /** 幂等写入：docId 定位 point，正文嵌入后 upsert */
    public void upsert(String docId, String text, String title, String category) {
        ensureCollection();
        long start = System.currentTimeMillis();
        try {
            float[] vec = embeddingModel.embed(text).content().vector();
            String pointId = UUID.nameUUIDFromBytes(docId.getBytes(StandardCharsets.UTF_8)).toString();
            String body = "{\"points\":[{"
                    + "\"id\":\"" + pointId + "\","
                    + "\"vector\":" + floatsToJson(vec) + ","
                    + "\"payload\":{" + payload(docId, title, category) + "}}]}";
            HttpResponse<String> resp = http("PUT", "/collections/" + collection + "/points?wait=true", body);
            log.info("[Qdrant] upsert docId={} → HTTP {}（{}ms）", docId, resp.statusCode(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[Qdrant] upsert 失败 docId={}: {}", docId, e.getMessage(), e);
        }
    }

    /** 向量检索：返回按相似度降序的命中（含分数），供调用方回表取正文 */
    public List<SearchHit> search(String query, int topK) {
        ensureCollection();
        long start = System.currentTimeMillis();
        try {
            float[] qvec = embeddingModel.embed(query).content().vector();
            String body = "{\"vector\":" + floatsToJson(qvec) + ",\"limit\":" + topK + ",\"with_payload\":true}";
            HttpResponse<String> resp = http("POST", "/collections/" + collection + "/points/search", body);
            if (resp.statusCode() != 200) {
                log.warn("[Qdrant] 检索非200: {} body={}", resp.statusCode(), truncate(resp.body()));
                return List.of();
            }
            JSONObject root = JSON.parseObject(resp.body());
            JSONArray arr = root.getJSONArray("result");
            List<SearchHit> hits = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    JSONObject pl = o.getJSONObject("payload");
                    String docId = pl != null ? pl.getString("docId") : null;
                    if (docId != null) {
                        hits.add(new SearchHit(docId, o.getFloatValue("score")));
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            for (SearchHit h : hits) {
                sb.append(sb.length() > 0 ? ", " : "").append(h.docId()).append("(").append(String.format("%.3f", h.score())).append(")");
            }
            log.info("[Qdrant] 检索 query='{}' topK={} 命中{}条 [{}]（{}ms）",
                    truncate(query), topK, hits.size(), sb, System.currentTimeMillis() - start);
            return hits;
        } catch (Exception e) {
            log.error("[Qdrant] 检索失败 query='{}': {}", truncate(query), e.getMessage(), e);
            return List.of();
        }
    }

    /** 删除某条知识的向量 */
    public void delete(String docId) {
        try {
            String pointId = UUID.nameUUIDFromBytes(docId.getBytes(StandardCharsets.UTF_8)).toString();
            String body = "{\"points\":[\"" + pointId + "\"]}";
            HttpResponse<String> resp = http("POST", "/collections/" + collection + "/points/delete?wait=true", body);
            log.info("[Qdrant] delete docId={} → HTTP {}", docId, resp.statusCode());
        } catch (Exception e) {
            log.error("[Qdrant] delete 失败 docId={}: {}", docId, e.getMessage(), e);
        }
    }

    /** collection 当前点数（便于排查/自检） */
    public long count() {
        try {
            HttpResponse<String> resp = http("GET", "/collections/" + collection, null);
            if (resp.statusCode() == 200) {
                JSONObject result = JSON.parseObject(resp.body()).getJSONObject("result");
                return result != null ? result.getLongValue("points_count") : -1;
            }
        } catch (Exception e) {
            log.warn("[Qdrant] count 失败: {}", e.getMessage());
        }
        return -1;
    }

    private HttpResponse<String> http(String method, String path, String body) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofMillis(timeoutMs));
        if (body == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String payload(String docId, String title, String category) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"docId\":\"").append(esc(docId)).append("\"");
        if (title != null) sb.append(",\"title\":\"").append(esc(title)).append("\"");
        if (category != null) sb.append(",\"category\":\"").append(esc(category)).append("\"");
        return sb.toString();
    }

    private static String floatsToJson(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        return sb.append("]").toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String truncate(String s) {
        return s != null && s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}
