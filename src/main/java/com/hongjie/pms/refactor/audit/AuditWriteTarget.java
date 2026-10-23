package com.hongjie.pms.refactor.audit;

public interface AuditWriteTarget {
    String type();
    void lock(Long id);
    Snapshot load(Long id);
    void decide(Long id, int status);
    record Snapshot(Long ownerId, String title, String notificationType) {}
}
