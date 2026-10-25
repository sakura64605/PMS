package com.hongjie.pms.modules.community.like.application;

public record LikeChanged(Long actorId, Long targetId, String targetType,
                          LikeTarget.Snapshot target, boolean liked) {}
