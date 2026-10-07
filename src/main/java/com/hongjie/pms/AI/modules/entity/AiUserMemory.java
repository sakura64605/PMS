package com.hongjie.pms.AI.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** AI 用户长期记忆：从对话沉淀的用户持久事实/偏好（跨会话生效）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_user_memory")
public class AiUserMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 记忆内容，如"养了一只橘猫叫圆圆" */
    private String content;

    /** 来源会话 */
    private String sourceSession;

    /** 重要度 1-5 */
    private Integer importance;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
