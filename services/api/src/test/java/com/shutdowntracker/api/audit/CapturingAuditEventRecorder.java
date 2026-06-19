package com.shutdowntracker.api.audit;

import java.util.ArrayList;
import java.util.List;

public class CapturingAuditEventRecorder implements AuditEventRecorder {

    private final List<AuditEventCreateRequest> events = new ArrayList<>();

    @Override
    public void record(AuditEventCreateRequest event) {
        events.add(event);
    }

    public List<AuditEventCreateRequest> events() {
        return List.copyOf(events);
    }

    public AuditEventCreateRequest singleEvent() {
        if (events.size() != 1) {
            throw new AssertionError("Expected exactly one audit event but recorded " + events.size());
        }
        return events.getFirst();
    }
}
