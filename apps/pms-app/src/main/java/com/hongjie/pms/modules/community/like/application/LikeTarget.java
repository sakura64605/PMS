package com.hongjie.pms.modules.community.like.application;

public interface LikeTarget {
    String type();

    Snapshot load(Long id);

    void increment(Long id);

    void decrement(Long id);

    record Snapshot(Long ownerId, String title, String link, int count) {}
}
