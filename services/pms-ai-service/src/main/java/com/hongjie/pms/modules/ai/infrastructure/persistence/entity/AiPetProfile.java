package com.hongjie.pms.modules.ai.infrastructure.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Data
@TableName("ai_pet_profile")
public class AiPetProfile {
 @TableId(type = IdType.AUTO) private Long id;
 private Long userId;
 private String name;
 private String species;
 private String breed;
 private LocalDate birthDate;
 private String sex;
 private LocalDateTime updatedAt;
}
