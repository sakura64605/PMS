package com.hongjie.pms.AI.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.entity.AiKnowledgeBase;
import com.hongjie.pms.AI.modules.mapper.AiKnowledgeBaseMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
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
        
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            List<EmbeddingMatch<String>> matches = embeddingStore.findRelevant(queryEmbedding, 3);
            
            if (matches.isEmpty()) {
                return null;
            }
            
            List<String> docIds = matches.stream()
                    .map(EmbeddingMatch::embedded)
                    .collect(Collectors.toList());
            
            List<AiKnowledgeBase> knowledges = knowledgeBaseMapper.selectBatchIds(docIds);
            
            StringBuilder sb = new StringBuilder();
            for (AiKnowledgeBase knowledge : knowledges) {
                sb.append("【").append(knowledge.getTitle()).append("】\n");
                sb.append(knowledge.getContent()).append("\n\n");
            }
            return sb.toString();
            
        } catch (Exception e) {
            log.error("RAG搜索失败", e);
            return null;
        }
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