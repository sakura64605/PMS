package com.hongjie.pms.common.enums;

import lombok.Getter;

@Getter
public enum MessageType {

    // 互动类
    LIKE("LIKE", "点赞"),
    COMMENT("COMMENT", "评论"),
    FOLLOW("FOLLOW", "关注"),

    // 活动类
    SIGN_UP("SIGN_UP", "报名"),
    SIGN_IN("SIGN_IN", "签到"),
    ACTIVITY_REMINDER("ACTIVITY_REMINDER", "活动提醒"),
    ACTIVITY_FULL("ACTIVITY_FULL", "活动满员"),

    // 审核类
    AUDIT_PASS("AUDIT_PASS", "审核通过"),
    AUDIT_REJECT("AUDIT_REJECT", "审核拒绝"),

    // 惩罚类
    PUNISHMENT_START("PUNISHMENT_START", "惩罚开始"),
    PUNISHMENT_END("PUNISHMENT_END", "惩罚结束"),
    NO_SHOW_WARNING("NO_SHOW_WARNING", "爽约警告"),

    // 系统类
    NOTICE("NOTICE", "系统公告"),
    SYSTEM("SYSTEM", "系统消息"),

    ACTIVITY_STATISTICS("ACTIVITY_STATISTICS", "活动统计报告");   // 活动统计报告

    private final String code;
    private final String name;

    MessageType(String code, String name) {
        this.code = code;
        this.name = name;
    }
}