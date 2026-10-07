package com.hongjie.pms.AI.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.entity.AiKnowledgeBase;
import com.hongjie.pms.AI.modules.mapper.AiKnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库服务：向量存储改为 Qdrant（{@link QdrantVectorStore}），MySQL {@code ai_knowledge_base} 仍是权威源。
 * <p>检索优先走 Qdrant 向量召回，无命中/异常时回退到 MySQL 关键词 LIKE（对中文更稳）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    /** 向量召回条数 */
    private static final int RECALL_TOP_K = 3;

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final QdrantVectorStore qdrantVectorStore;

    @PostConstruct
    public void init() {
        loadKnowledgeToVectorStore();
    }

    /** 全量把启用中的知识同步进 Qdrant（按 docId 幂等 upsert），来源仍是 MySQL */
    public void loadKnowledgeToVectorStore() {
        LambdaQueryWrapper<AiKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiKnowledgeBase::getStatus, 1);
        List<AiKnowledgeBase> knowledgeList = knowledgeBaseMapper.selectList(wrapper);

        long start = System.currentTimeMillis();
        log.info("[知识库] 开始同步 {} 条到 Qdrant ...", knowledgeList.size());
        for (AiKnowledgeBase knowledge : knowledgeList) {
            qdrantVectorStore.upsert(knowledge.getDocId(), knowledge.getContent(), knowledge.getTitle(), knowledge.getCategory());
        }
        log.info("[知识库] 同步完成，共 {} 条，Qdrant 当前点数={}（{}ms）",
                knowledgeList.size(), qdrantVectorStore.count(), System.currentTimeMillis() - start);
    }

    public String searchRelevant(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        // 1. Qdrant 向量召回
        try {
            List<QdrantVectorStore.SearchHit> hits = qdrantVectorStore.search(query, RECALL_TOP_K);
            if (!hits.isEmpty()) {
                List<String> docIds = hits.stream().map(QdrantVectorStore.SearchHit::docId).toList();
                List<AiKnowledgeBase> rows = knowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<AiKnowledgeBase>().in(AiKnowledgeBase::getDocId, docIds));

                // 按命中顺序（相似度降序）重排
                Map<String, AiKnowledgeBase> byDocId = new LinkedHashMap<>();
                for (AiKnowledgeBase row : rows) {
                    byDocId.put(row.getDocId(), row);
                }
                StringBuilder sb = new StringBuilder();
                for (QdrantVectorStore.SearchHit hit : hits) {
                    AiKnowledgeBase k = byDocId.get(hit.docId());
                    if (k != null) {
                        sb.append("【").append(k.getTitle()).append("】\n");
                        sb.append(k.getContent()).append("\n\n");
                    }
                }
                if (sb.length() > 0) {
                    log.info("[知识库] Qdrant 向量召回命中 {} 条 query='{}'", hits.size(), query);
                    return sb.toString();
                }
            }
            log.info("[知识库] Qdrant 无命中，回退关键词搜索 query='{}'", query);
        } catch (Exception e) {
            log.warn("[知识库] Qdrant 检索异常，回退关键词搜索：{}", e.getMessage());
        }

        // 2. 回退：关键词 LIKE（仅当存在 ≥2 字关键词时才拼条件，避免空条件 SQL 语法错误）
        try {
            LambdaQueryWrapper<AiKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiKnowledgeBase::getStatus, 1);
            List<String> validKeywords = new ArrayList<>();
            for (String kw : query.split("[\\s,，、。.]")) {
                String t = kw.trim();
                if (t.length() >= 2) {
                    validKeywords.add(t);
                }
            }
            if (!validKeywords.isEmpty()) {
                wrapper.and(w -> {
                    boolean first = true;
                    for (String kw : validKeywords) {
                        if (first) {
                            w.like(AiKnowledgeBase::getTitle, kw)
                                    .or()
                                    .like(AiKnowledgeBase::getContent, kw);
                            first = false;
                        } else {
                            w.or(i -> i.like(AiKnowledgeBase::getTitle, kw)
                                    .or()
                                    .like(AiKnowledgeBase::getContent, kw));
                        }
                    }
                });
            }
            wrapper.last("LIMIT 3");
            List<AiKnowledgeBase> knowledges = knowledgeBaseMapper.selectList(wrapper);

            if (!knowledges.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (AiKnowledgeBase knowledge : knowledges) {
                    sb.append("【").append(knowledge.getTitle()).append("】\n");
                    sb.append(knowledge.getContent()).append("\n\n");
                }
                log.info("[知识库] 关键词搜索命中 {} 条 query='{}'", knowledges.size(), query);
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("[知识库] 关键词搜索失败", e);
        }

        return null;
    }

    /** 新增知识：写 MySQL（权威源）+ 同步进 Qdrant */
    public void addKnowledge(String title, String content, String category, List<String> tags) {
        AiKnowledgeBase knowledge = new AiKnowledgeBase();
        knowledge.setDocId(UUID.randomUUID().toString());
        knowledge.setTitle(title);
        knowledge.setContent(content);
        knowledge.setContentType("faq");
        knowledge.setCategory(category);
        knowledge.setTags(tags);
        knowledge.setStatus(1);
        knowledgeBaseMapper.insert(knowledge);

        qdrantVectorStore.upsert(knowledge.getDocId(), content, title, category);
        log.info("[知识库] 新增知识 docId={} title={}，已写入 MySQL + Qdrant", knowledge.getDocId(), title);
    }

    /** 下架知识：改 MySQL 状态为停用 + 删除 Qdrant 向量 */
    public void removeKnowledge(Long id) {
        AiKnowledgeBase knowledge = knowledgeBaseMapper.selectById(id);
        if (knowledge == null) {
            log.warn("[知识库] 待删除知识不存在 id={}", id);
            return;
        }
        knowledge.setStatus(0);
        knowledgeBaseMapper.updateById(knowledge);
        qdrantVectorStore.delete(knowledge.getDocId());
        log.info("[知识库] 下架知识 id={} docId={}，已同步 Qdrant 删除", id, knowledge.getDocId());
    }
}
