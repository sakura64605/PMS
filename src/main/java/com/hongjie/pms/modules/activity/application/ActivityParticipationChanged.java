package com.hongjie.pms.modules.activity.application;

/** Immutable notification facts captured while the activity row is locked. */
public record ActivityParticipationChanged(Long activityId, Long ownerId, Long participantId,
                                            String title, String name, boolean full, Kind kind) {
    public enum Kind { SIGNED_UP, CHECKED_IN }
}
