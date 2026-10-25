package com.hongjie.pms.modules.activity.application;

/** Publish only facts: consumers reload the committed activity instead of indexing a stale entity. */
public record ActivityContentChanged(Long activityId) {}
