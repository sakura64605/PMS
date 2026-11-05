package com.hongjie.pms.modules.ai.infrastructure.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Data
@TableName("ai_pet_record")
public class AiPetRecord {
 @TableId(type=IdType.AUTO) private Long id;
 private Long userId;
 private Long petId;
 private String kind;
 private String content;
 private LocalDate occurredOn;
 private String eventState;
 private String sourceMessageId;
 private Integer status;
 private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
}
