package com.hongjie.pms.refactor.ai;
public enum AiSessionState {
    DELETED(0),ACTIVE(1),HUMAN(3);
    public final int code;
    AiSessionState(int code){this.code=code;}
}
