package com.hongjie.pms.platform.circuitbreaker;

@FunctionalInterface
public interface FallbackFunction {
    Object apply(Exception e);
}
