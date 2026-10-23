package com.hongjie.pms.refactor.core.event.outbox;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发件箱记录，对应新增表 {@code pms_domain_event}。
 *
 * <p>刻意<b>不继承 {@code BaseEntity}</b>：该表的列名是 {@code created_at}（而非
 * {@code create_time}），且没有 {@code deleted} 逻辑删除列。这恰好演示了
 * {@code CoreMetaObjectHandler} 的注解驱动填充 —— 它按注解填，不按字段名填，
 * 所以 {@code created_at} 也能被自动填充（老实现做不到这一点）。
 *
 * <p>{@code status} 字段类型保持 {@code Integer} 而非 {@link OutboxStatus}：
 * 项目现有 31 个实体的状态字段全部是 {@code Integer}，保持一致；
 * 且这样不依赖 MyBatis-Plus 的枚举类型处理器配置，避免了本项目无法在本地
 * 完整启动（依赖 MySQL/Redis/RocketMQ）时难以验证的运行期风险。
 * 取值一律由 {@link OutboxStatus#getCode()} 提供，不出现裸数字。
 */
@Data
@TableName("pms_domain_event")
public class PmsDomainEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一 ID，消费端据此幂等（发件箱是至少一次投递） */
    @TableField("event_id")
    private String eventId;

    @TableField("event_type")
    private String eventType;

    /** 载荷全限定类名，供反序列化还原具体事件类型 */
    @TableField("payload_class")
    private String payloadClass;

    @TableField("aggregate_type")
    private String aggregateType;

    @TableField("aggregate_id")
    private Long aggregateId;

    /** 事件载荷 JSON */
    @TableField("payload")
    private String payload;

    @TableField("status")
    private Integer status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @TableField("last_error")
    private String lastError;

    /** 注解驱动填充：列名是 created_at，老实现按字段名填充因而无法覆盖 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("sent_at")
    private LocalDateTime sentAt;
}
