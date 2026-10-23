package com.hongjie.pms.refactor.following;

/** Notification facts captured inside the transaction; no ThreadLocal dependency at delivery. */
public record FollowCreated(Long followerId,Long followingId,String followerName) {}
