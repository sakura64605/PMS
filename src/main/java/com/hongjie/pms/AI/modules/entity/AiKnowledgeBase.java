package com.hongjie.pms.AI.modules.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "ai_knowledge_base", autoResultMap = true)
public class AiKnowledgeBase {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String docId;
    
    private String title;
    
    private String content;
    
    private String contentType;
    
    private String category;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}