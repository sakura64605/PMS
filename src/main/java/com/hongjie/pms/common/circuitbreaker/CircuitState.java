package com.hongjie.pms.common.circuitbreaker;

public enum CircuitState {
    CLOSED,     // 关闭（正常）
    OPEN,       // 打开（熔断）
    HALF_OPEN   // 半开（探测）
}