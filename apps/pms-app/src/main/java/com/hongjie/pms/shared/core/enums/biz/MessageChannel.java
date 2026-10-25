package com.hongjie.pms.shared.core.enums.biz;

import com.hongjie.pms.shared.core.enums.CodableEnum;

/**
 * 消息投递通道。
 *
 * <p>项目里目前并存四套互不相通的消息载体（{@code user_message} 站内信、私信表、
 * {@code notice} 公告、{@code user_inbox} 动态收件箱），各自有自己的 service 与推送链路。
 * 本枚举是它们的统一标识，用于日志、路由与「按通道退订」等横切能力。
 *
 * <p>这是<b>投递通道轴</b>，与 {@code common.enums.MessageType}（LIKE/COMMENT/FOLLOW 业务类型轴）
 * 以及 {@link com.hongjie.pms.shared.core.enums.BizTargetType}（互动目标轴）三者彼此正交。
 */
public enum MessageChannel implements CodableEnum<String> {

    INBOX("inbox", "站内信"),
    PRIVATE("private", "私信"),
    NOTICE("notice", "系统公告"),
    FEED("feed", "动态收件箱");

    private final String code;
    private final String desc;

    MessageChannel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    /** 走 RocketMQ 异步推送的通道（公告是拉取式，不入推送队列） */
    public boolean isPushedAsync() {
        return this == INBOX || this == PRIVATE;
    }
}
