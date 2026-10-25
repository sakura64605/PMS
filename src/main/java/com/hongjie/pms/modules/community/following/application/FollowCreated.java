package com.hongjie.pms.modules.community.following.application;

/** Notification facts captured inside the transaction; no ThreadLocal dependency at delivery. */
public record FollowCreated(Long followerId,Long followingId,String followerName) {}
