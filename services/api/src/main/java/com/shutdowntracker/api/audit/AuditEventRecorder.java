package com.shutdowntracker.api.audit;

public interface AuditEventRecorder {

    void record(AuditEventCreateRequest event);
}
