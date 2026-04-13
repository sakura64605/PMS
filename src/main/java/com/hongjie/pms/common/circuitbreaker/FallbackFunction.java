package com.hongjie.pms.common.circuitbreaker;

@FunctionalInterface
public interface FallbackFunction {
    Object apply(Exception e);
}