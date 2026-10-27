package com.hongjie.pms.platform.circuitbreaker;

@FunctionalInterface
public interface SupplierWithThrowable<T> {
    T get() throws Exception;
}
