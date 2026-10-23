package com.hongjie.pms.refactor.core.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带完整审计、逻辑删除与乐观锁的实体基类，对应<b>新表</b>的结构。
 *
 * <p>相对 {@link BaseEntity} 多出四列：{@code creator}、{@code updater}、
 * {@code deleted}、{@code version}。这四列老表普遍没有，
 * 所以只有指向新表的实体才继承本类；迁移期映射老表的实体先用 {@link BaseEntity}，
 * 切表时改成继承本类即可。
 *
 * <p><b>为什么 {@code deleted} 没有标 {@code @TableLogic}</b>：
 * MyBatis-Plus 的逻辑删除会在所有自动生成的查询上强制追加 {@code deleted = 0}，
 * 于是「查询已删除记录」就变得无法表达 —— 而本系统有<b>回收站</b>、
 * <b>恢复</b>、<b>彻底删除</b>三个真实功能，都需要读到 {@code deleted = 1} 的行。
 * 若保留 {@code @TableLogic}，这些查询只能退化成裸 SQL 或自定义 {@code @Select}，
 * 还得额外处理 JSON 列的 typeHandler 映射。
 *
 * <p>所以这里把 {@code deleted} 当作<b>普通列</b>，由查询显式过滤：
 * 代价是每个查询都要自己带上 {@code deleted = 0}（由
 * {@code AbstractBaseService.baseQuery()} 统一承担），
 * 收益是回收站这类需求不需要任何特殊处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AuditEntity extends BaseEntity {

    /** 创建人；无登录上下文（MQ 消费、定时任务）时为 null */
    @TableField(value = "creator", fill = FieldFill.INSERT)
    private Long creator;

    /** 更新人；无登录上下文时为 null */
    @TableField(value = "updater", fill = FieldFill.INSERT_UPDATE)
    private Long updater;

    /** 逻辑删除：0-正常 1-已删除。刻意不标 {@code @TableLogic}，理由见类注释 */
    @TableField("deleted")
    private Integer deleted;

    /** 乐观锁版本号：UPDATE 自动带版本条件，并发冲突时更新 0 行而非静默覆盖 */
    @Version
    @TableField("version")
    private Integer version;
}
