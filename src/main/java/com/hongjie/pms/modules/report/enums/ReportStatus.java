package com.hongjie.pms.modules.report.enums;

public enum ReportStatus {
    PENDING(0, "待处理"),
    HANDLED(1, "已处理"),
    REJECTED(2, "已驳回");

    private final Integer code;
    private final String desc;

    ReportStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}