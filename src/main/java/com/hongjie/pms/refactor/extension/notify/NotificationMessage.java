package com.hongjie.pms.refactor.extension.notify;

import com.hongjie.pms.common.enums.MessageType;

/**
 * 待落库与推送的通知内容 —— 与 {@code user_message} 表结构同构的不可变值对象。
 *
 * <p>它是策略（{@code NotificationHandler}）的输出、模板方法（{@code NotificationFacade}）
 * 的输入：把「文案怎么算」与「怎么落库、怎么推送」彻底分开。
 *
 * <p>复用老枚举 {@link MessageType} 而非另建一套 —— {@code user_message.type} 是消息类型轴，
 * 与目标类型轴正交，没有必要重复定义。
 */
public record NotificationMessage(
        Long userId,
        Long senderId,
        MessageType type,
        String title,
        String content,
        Long businessId,
        String link) {
}
