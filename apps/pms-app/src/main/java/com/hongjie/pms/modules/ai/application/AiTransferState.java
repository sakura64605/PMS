package com.hongjie.pms.modules.ai.application;
public enum AiTransferState {
    REQUESTED(1),ACCEPTED(2),CLOSED(3);
    public final int code;
    AiTransferState(int code){this.code=code;}
}
