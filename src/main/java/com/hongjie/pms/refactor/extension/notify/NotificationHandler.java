package com.hongjie.pms.refactor.extension.notify;
import com.hongjie.pms.refactor.message.api.MessageService;

import com.hongjie.pms.common.enums.MessageType;

/**
 * 通知策略：一种消息类型一个实现，负责「把上下文渲染成通知内容」。
 *
 * <p>替代 {@code MessageService} 里 20 个 {@code sendXxxNotification} 方法 ——
 * 那些方法把「拼文案」「建实体」「落库」「推送」四件事揉在一起，
 * 本接口只负责第一件，后三件由 {@code NotificationFacade} 的模板方法统一完成。
 *
 * <p><b>与数据库模板表的分工</b>：纯静态文案的通知（点赞、关注）可以直接由
 * {@code pms_message_template} 表驱动；但含业务计算的通知
 * （如活动统计报告要做 {@code String.format("报名 %d 人，签到 %d 人…")}）用模板表达会退化成 SpEL，
 * 更难维护。所以按「是否需要业务计算」分流，而不是强行全 DB 化。
 *
 * @param <C> 该通知类型对应的上下文
 */
public interface NotificationHandler<C extends NotificationContext> {

    /** 本处理器负责的消息类型 */
    MessageType type();

    /** 渲染通知内容 */
    NotificationMessage build(C ctx);
}
