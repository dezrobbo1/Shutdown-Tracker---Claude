package com.shutdowntracker.api.exportpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.audit.CapturingAuditEventRecorder;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ExportCandidateServiceTests {

    @Test
    void createsUnapprovedCandidateFromServerNormalizedValue() {
        UUID projectId = UUID.randomUUID();
        ExportPreviewRepository repository = mock(ExportPreviewRepository.class);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportCandidateService service = new ExportCandidateService(repository, audit);
        ExportCandidateCreateRequest request = request("percent_complete", "075.0");
        ExportCandidateRecord stored = candidate(projectId, request, "75");
        when(repository.createAuthoritativeCandidate(projectId, request, "75")).thenReturn(stored);

        ExportCandidateRecord created = service.createCandidate(projectId, request);

        assertThat(created).isEqualTo(stored);
        assertThat(created.normalizedNewValue()).isEqualTo("75");
        assertThat(audit.singleEvent().eventType()).isEqualTo(AuditEventTypes.EXPORT_CANDIDATE_CREATED);
        assertThat(audit.singleEvent().newValueSummary()).containsEntry("approvalState", "not_approved");
        verify(repository, never()).createCandidateApprovalEvent(
                eq(projectId),
                eq(stored.id()),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void preservesInternalPhysicalPercentDecimalsWithoutMakingAnApproval() {
        UUID projectId = UUID.randomUUID();
        ExportPreviewRepository repository = mock(ExportPreviewRepository.class);
        ExportCandidateService service = new ExportCandidateService(repository, new CapturingAuditEventRecorder());
        ExportCandidateCreateRequest request = request("physical_percent_complete", "075.500");
        ExportCandidateRecord stored = candidate(projectId, request, "75.5");
        when(repository.createAuthoritativeCandidate(projectId, request, "75.5")).thenReturn(stored);

        ExportCandidateRecord created = service.createCandidate(projectId, request);

        assertThat(created.normalizedNewValue()).isEqualTo("75.5");
        assertThat(ExportPreviewField.fromFieldName(created.fieldName()).mvpExportAuthorized()).isFalse();
    }

    @Test
    void failsClosedWhenDatabaseRejectsSnapshotTaskOrIdentityAuthority() {
        UUID projectId = UUID.randomUUID();
        ExportPreviewRepository repository = mock(ExportPreviewRepository.class);
        ExportCandidateService service = new ExportCandidateService(repository, new CapturingAuditEventRecorder());
        ExportCandidateCreateRequest request = request("actual_finish", "2026-01-06T15:00:00Z");
        when(repository.createAuthoritativeCandidate(
                projectId,
                request,
                "2026-01-06T15:00:00Z"
        )).thenThrow(new DataIntegrityViolationException("Synthetic rejected imported task identity."));

        assertThatThrownBy(() -> service.createCandidate(projectId, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("accepted snapshot")
                .hasMessageContaining("matching imported task");
    }

    @Test
    void recordsApprovalAsASeparateCandidateBoundEvent() {
        UUID projectId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        ExportPreviewRepository repository = mock(ExportPreviewRepository.class);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportCandidateService service = new ExportCandidateService(repository, audit);
        ExportCandidateApprovalEventRequest request = new ExportCandidateApprovalEventRequest(
                ApprovalState.APPROVED_FOR_EXPORT,
                OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-01-01T08:00:00Z"),
                "Synthetic planner approval",
                Map.of("source", "synthetic-test")
        );
        ExportCandidateApprovalEventRecord stored = new ExportCandidateApprovalEventRecord(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                candidateId,
                ExportIntegrityPolicy.CURRENT_VERSION,
                ApprovalState.APPROVED_FOR_EXPORT,
                null,
                request.requestedAt(),
                request.reviewedByUserId(),
                request.reviewedAt(),
                request.reason(),
                OffsetDateTime.parse("2026-01-01T08:00:00Z"),
                request.metadata()
        );
        when(repository.createCandidateApprovalEvent(projectId, candidateId, request))
                .thenReturn(Optional.of(stored));

        ExportCandidateApprovalEventRecord created = service.recordApprovalEvent(projectId, candidateId, request);

        assertThat(created.authoritativeExportCandidateId()).isEqualTo(candidateId);
        assertThat(created.approvalState()).isEqualTo(ApprovalState.APPROVED_FOR_EXPORT);
        assertThat(audit.singleEvent().eventType())
                .isEqualTo(AuditEventTypes.EXPORT_CANDIDATE_APPROVAL_RECORDED);
    }

    private ExportCandidateCreateRequest request(String fieldName, String proposedValue) {
        return new ExportCandidateCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                fieldName,
                proposedValue,
                "task_update",
                UUID.randomUUID(),
                "synthetic-source-version-1",
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                "Synthetic reason",
                Map.of("source", "synthetic-test")
        );
    }

    private ExportCandidateRecord candidate(
            UUID projectId,
            ExportCandidateCreateRequest request,
            String normalizedNewValue
    ) {
        return new ExportCandidateRecord(
                UUID.randomUUID(),
                ExportIntegrityPolicy.CURRENT_VERSION,
                projectId,
                request.projectSnapshotId(),
                request.importedTaskId(),
                request.sourceEntityType(),
                request.sourceEntityId(),
                request.sourceVersion(),
                request.fieldName(),
                "25",
                normalizedNewValue,
                "a".repeat(64),
                "101",
                "1",
                "Synthetic Task A1",
                true,
                request.sourceActorUserId(),
                request.sourceTimestamp(),
                request.reason(),
                OffsetDateTime.parse("2026-01-01T07:00:01Z"),
                request.metadata()
        );
    }
}
