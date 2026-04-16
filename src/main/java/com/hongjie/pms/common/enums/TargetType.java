package com.hongjie.pms.common.enums;

public enum TargetType {
    ADOPT("adopt", "领养"),
    HELP("help", "救助"),
    ACTIVITY("activity", "活动");

    private final String code;
    private final String desc;

    TargetType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}