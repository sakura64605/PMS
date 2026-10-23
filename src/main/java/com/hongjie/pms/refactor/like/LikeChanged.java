package com.hongjie.pms.refactor.like;

public record LikeChanged(Long actorId, Long targetId, String targetType,
                          LikeTarget.Snapshot target, boolean liked) {}
