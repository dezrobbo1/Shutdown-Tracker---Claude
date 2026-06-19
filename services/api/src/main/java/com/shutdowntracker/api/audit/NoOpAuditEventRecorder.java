package com.shutdowntracker.api.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "shutdown-tracker.persistence",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpAuditEventRecorder implements AuditEventRecorder {

    @Override
    public void record(AuditEventCreateRequest event) {
        // Review/test profiles can boot without PostgreSQL; local persistence records audit rows.
    }
}
