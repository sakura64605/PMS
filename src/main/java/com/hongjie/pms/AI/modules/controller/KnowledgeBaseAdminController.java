package com.hongjie.pms.AI.modules.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.AI.modules.entity.AiKnowledgeBase;
import com.hongjie.pms.AI.modules.mapper.AiKnowledgeBaseMapper;
import com.hongjie.pms.AI.rag.KnowledgeBaseService;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/pet-system/admin/ai/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseAdminController {
    
    private final KnowledgeBaseService knowledgeBaseService;
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    
    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }
    
    @PostMapping("/add")
    public CommonResult<String> addKnowledge(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam String category,
            @RequestParam(required = false) List<String> tags) {
        checkAdmin();
        knowledgeBaseService.addKnowledge(title, content, category, tags);
        return CommonResult.success("知识添加成功");
    }
    
    @GetMapping("/search")
    public CommonResult<IPage<AiKnowledgeBase>> searchKnowledge(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        checkAdmin();
        
        Page<AiKnowledgeBase> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(AiKnowledgeBase::getTitle, keyword)
                    .or().like(AiKnowledgeBase::getContent, keyword));
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq(AiKnowledgeBase::getCategory, category);
        }
        wrapper.eq(AiKnowledgeBase::getStatus, 1);
        
        return CommonResult.success(knowledgeBaseMapper.selectPage(page, wrapper));
    }
    
    @DeleteMapping("/delete/{id}")
    public CommonResult<String> deleteKnowledge(@PathVariable Long id) {
        checkAdmin();
        
        AiKnowledgeBase knowledge = knowledgeBaseMapper.selectById(id);
        if (knowledge != null) {
            knowledge.setStatus(0);
            knowledgeBaseMapper.updateById(knowledge);
        }
        return CommonResult.success("知识删除成功");
    }
    
    @PostMapping("/reload")
    public CommonResult<String> reloadKnowledgeBase() {
        checkAdmin();
        knowledgeBaseService.loadKnowledgeToVectorStore();
        return CommonResult.success("知识库重新加载成功");
    }
}