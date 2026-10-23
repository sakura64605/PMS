package com.hongjie.pms.refactor.core.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类：主键 + 时间戳。
 *
 * <p>只包含<b>迁移期与目标结构都具备</b>的列，因此可以<b>立即</b>被映射到老表的实体继承。
 * 项目大量老表（pet_post / comment / activity_signup…）本来就有
 * {@code id / create_time / update_time}。
 *
 * <p>更完整的审计能力（创建人、更新人、逻辑删除、乐观锁）在
 * {@link AuditEntity} 里 —— 那些列老表普遍没有，只有新表才有。
 * 把两者分开，是为了让「迁移期复用已有列」和「切表后启用完整审计」两件事
 * 不必互相等待。
 *
 * <p>字段填充由 {@code CoreMetaObjectHandler} 负责；它按注解填充而非按字段名，
 * 所以新表若用 {@code created_at} 这类列名也能生效。
 *
 * @see AuditEntity
 */
@Data
public abstract class BaseEntity implements Identifiable, Auditable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
