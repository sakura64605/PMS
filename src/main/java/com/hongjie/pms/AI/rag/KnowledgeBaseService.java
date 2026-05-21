package com.hongjie.pms.AI.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.entity.AiKnowledgeBase;
import com.hongjie.pms.AI.modules.mapper.AiKnowledgeBaseMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {
    
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore embeddingStore;
    
    @PostConstruct
    public void init() {
        loadKnowledgeToVectorStore();
    }
    
    public void loadKnowledgeToVectorStore() {
        LambdaQueryWrapper<AiKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiKnowledgeBase::getStatus, 1);
        List<AiKnowledgeBase> knowledgeList = knowledgeBaseMapper.selectList(wrapper);
        
        for (AiKnowledgeBase knowledge : knowledgeList) {
            Embedding embedding = embeddingModel.embed(knowledge.getContent()).content();
            embeddingStore.add(knowledge.getDocId(), embedding);
        }
        
        log.info("知识库加载完成，共 {} 条", knowledgeList.size());
    }
    
    public String searchRelevant(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        // 1. 先尝试向量搜索（AllMiniLmL6V2 是英文模型，对中文效果可能不佳）
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(3)
                            .build();
                    EmbeddingSearchResult<String> searchResult = embeddingStore.search(searchRequest);
                    List<EmbeddingMatch<String>> matches = searchResult.matches();

                    if (!matches.isEmpty()) {
                List<String> docIds = matches.stream()
                        .map(EmbeddingMatch::embedded)
                        .collect(Collectors.toList());

                LambdaQueryWrapper<AiKnowledgeBase> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.in(AiKnowledgeBase::getDocId, docIds);
                List<AiKnowledgeBase> knowledges = knowledgeBaseMapper.selectList(queryWrapper);

                if (!knowledges.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (AiKnowledgeBase knowledge : knowledges) {
                        sb.append("【").append(knowledge.getTitle()).append("】\n");
                        sb.append(knowledge.getContent()).append("\n\n");
                    }
                    log.info("向量搜索命中 {} 条知识", knowledges.size());
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            log.warn("向量搜索失败，回退到关键词搜索", e);
        }

        // 2. 向量搜索无结果 → 关键词 LIKE 搜索（对中文更可靠）
        try {
            LambdaQueryWrapper<AiKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiKnowledgeBase::getStatus, 1);
            wrapper.and(w -> {
                // 对整个查询词做 LIKE 匹配
                String[] keywords = query.split("[\\s,，、。.]");
                for (String kw : keywords) {
                    if (kw.trim().length() >= 2) {
                        w.like(AiKnowledgeBase::getTitle, kw.trim())
                                .or()
                                .like(AiKnowledgeBase::getContent, kw.trim());
                    }
                }
            });
            wrapper.last("LIMIT 3");
            List<AiKnowledgeBase> knowledges = knowledgeBaseMapper.selectList(wrapper);

            if (!knowledges.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (AiKnowledgeBase knowledge : knowledges) {
                    sb.append("【").append(knowledge.getTitle()).append("】\n");
                    sb.append(knowledge.getContent()).append("\n\n");
                }
                log.info("关键词搜索命中 {} 条知识", knowledges.size());
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("关键词搜索失败", e);
        }

        return null;
    }
    
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
        
        Embedding embedding = embeddingModel.embed(content).content();
        embeddingStore.add(knowledge.getDocId(), embedding);
    }
}