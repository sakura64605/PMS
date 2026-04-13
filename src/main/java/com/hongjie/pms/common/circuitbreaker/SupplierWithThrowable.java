package com.hongjie.pms.common.circuitbreaker;

@FunctionalInterface
public interface SupplierWithThrowable<T> {
    T get() throws Exception;
}