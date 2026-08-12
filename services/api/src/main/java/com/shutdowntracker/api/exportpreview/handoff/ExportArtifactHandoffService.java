package com.shutdowntracker.api.exportpreview.handoff;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.exportpreview.ExportBatchGeneratedRequest;
import com.shutdowntracker.api.exportpreview.ExportBatchState;
import com.shutdowntracker.api.exportpreview.ExportPreviewDetail;
import com.shutdowntracker.api.exportpreview.ExportPreviewLineRecord;
import com.shutdowntracker.api.exportpreview.ExportPreviewService;
import com.shutdowntracker.api.exportpreview.storage.ExportArtifactStorage;
import com.shutdowntracker.api.exportpreview.storage.ExportArtifactStorageLocation;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ExportArtifactHandoffService {

    private static final String GENERATED_MESSAGE = "Worker-generated MSPDI/XML artifact metadata recorded. "
            + "No Microsoft Project write-back was run.";
    private static final String WORKER_PROJECT_NAME_PREFIX = "Shutdown Tracker Export Batch ";

    private final ExportPreviewService exportPreviewService;
    private final ProjectExportArtifactJobClient exportArtifactJobClient;
    private final ExportArtifactStorage exportArtifactStorage;

    public ExportArtifactHandoffService(
            ExportPreviewService exportPreviewService,
            ProjectExportArtifactJobClient exportArtifactJobClient,
            ExportArtifactStorage exportArtifactStorage
    ) {
        this.exportPreviewService = exportPreviewService;
        this.exportArtifactJobClient = exportArtifactJobClient;
        this.exportArtifactStorage = exportArtifactStorage;
    }

    /**
     * Deliberately not {@code @Transactional}.
     *
     * <p>This method calls the project worker over HTTP, and the worker writes the artifact to disk before
     * returning. Holding a database transaction across that call would pin a connection for the life of a
     * remote request. {@link ExportPreviewService#markGenerated} opens its own transaction to record the
     * returned artifact metadata once the worker has responded and the response has been verified.
     */
    public ExportArtifactGenerationResponse generateArtifact(
            UUID projectId,
            UUID exportBatchId,
            Actor actor,
            ExportArtifactGenerationRequest request
    ) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = Objects.requireNonNull(exportBatchId, "exportBatchId is required.");
        Actor requiredActor = Objects.requireNonNull(actor, "actor is required.");
        ExportArtifactGenerationRequest requiredRequest = request == null
                ? ExportArtifactGenerationRequest.empty()
                : request;

        ExportPreviewDetail approvedPreview = exportPreviewService.getPreview(requiredProjectId, requiredExportBatchId);
        if (approvedPreview.batch().status() != ExportBatchState.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only approved export batches can request worker artifact generation."
            );
        }

        ExportArtifactStorageLocation storageLocation =
                exportArtifactStorage.prepareExportArtifact(requiredProjectId, requiredExportBatchId);
        ProjectExportArtifactGenerationRequest workerRequest = buildWorkerRequest(approvedPreview, storageLocation);
        ProjectExportArtifactGenerationResponse workerResponse =
                exportArtifactJobClient.generateArtifact(workerRequest);
        verifyWorkerResponse(requiredProjectId, requiredExportBatchId, storageLocation, workerResponse);

        ExportPreviewDetail generatedPreview = exportPreviewService.markGenerated(
                requiredProjectId,
                requiredExportBatchId,
                requiredActor,
                new ExportBatchGeneratedRequest(
                        workerResponse.exportFileUri(),
                        workerResponse.exportFileHash(),
                        reason(requiredRequest),
                        generatedMetadata(requiredRequest, storageLocation, workerResponse)
                )
        );

        return new ExportArtifactGenerationResponse(generatedPreview, workerResponse, GENERATED_MESSAGE);
    }

    ProjectExportArtifactGenerationRequest buildWorkerRequest(
            ExportPreviewDetail preview,
            ExportArtifactStorageLocation storageLocation
    ) {
        List<ExportPreviewLineRecord> eligibleLines = preview.lines().stream()
                .filter(ExportPreviewLineRecord::exportEligible)
                .toList();
        if (eligibleLines.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Approved export batch must contain at least one eligible line."
            );
        }

        Map<UUID, ExportTaskBuilder> taskBuilders = new LinkedHashMap<>();
        for (ExportPreviewLineRecord line : eligibleLines) {
            if (!line.leafTask()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Only leaf-task export lines can be sent to the worker."
                );
            }
            ExportTaskBuilder builder = taskBuilders.computeIfAbsent(
                    line.importedTaskId(),
                    ignored -> ExportTaskBuilder.fromLine(line)
            );
            builder.addFieldValue(new ProjectExportArtifactFieldValue(
                    ProjectExportArtifactField.fromFieldName(line.fieldName()),
                    line.newValue()
            ));
        }

        return new ProjectExportArtifactGenerationRequest(
                preview.batch().id(),
                preview.batch().projectId(),
                storageLocation.outputPath().toString(),
                new ProjectExportArtifactRequest(
                        WORKER_PROJECT_NAME_PREFIX + preview.batch().id(),
                        taskBuilders.values().stream()
                                .map(ExportTaskBuilder::build)
                                .toList()
                )
        );
    }

    private void verifyWorkerResponse(
            UUID projectId,
            UUID exportBatchId,
            ExportArtifactStorageLocation storageLocation,
            ProjectExportArtifactGenerationResponse workerResponse
    ) {
        if (!exportBatchId.equals(workerResponse.exportBatchId())) {
            throw new IllegalStateException("Worker export artifact response referenced a different export batch.");
        }
        if (!projectId.equals(workerResponse.projectId())) {
            throw new IllegalStateException("Worker export artifact response referenced a different project.");
        }
        if (!storageLocation.storageUri().equals(workerResponse.exportFileUri())) {
            throw new IllegalStateException("Worker export artifact response did not match the reserved storage URI.");
        }
    }

    private String reason(ExportArtifactGenerationRequest request) {
        if (request.reason() != null && !request.reason().isBlank()) {
            return request.reason();
        }
        return GENERATED_MESSAGE;
    }

    private Map<String, Object> generatedMetadata(
            ExportArtifactGenerationRequest request,
            ExportArtifactStorageLocation storageLocation,
            ProjectExportArtifactGenerationResponse workerResponse
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
        metadata.put("workerArtifactGenerated", true);
        metadata.put("projectWriteBack", false);
        metadata.put("artifactStorageManagedByApi", true);
        metadata.put("artifactStorageKind", storageLocation.storageKind());
        metadata.put("artifactStorageUri", storageLocation.storageUri());
        metadata.put("artifactFilename", storageLocation.artifactFilename());
        metadata.put("artifactFormat", workerResponse.artifactSummary().artifactFormat());
        metadata.put("artifactTaskCount", workerResponse.artifactSummary().taskCount());
        metadata.put("artifactExportedFieldCount", workerResponse.artifactSummary().exportedFieldCount());
        metadata.put("artifactSizeBytes", workerResponse.artifactSummary().sizeBytes());
        metadata.put("workerMessage", workerResponse.message());
        return metadata;
    }

    private static class ExportTaskBuilder {

        private final UUID importedTaskId;
        private final String microsoftProjectTaskUid;
        private final String microsoftProjectTaskId;
        private final String taskName;
        private final boolean leafTask;
        private final List<ProjectExportArtifactFieldValue> fieldValues = new ArrayList<>();

        private ExportTaskBuilder(
                UUID importedTaskId,
                String microsoftProjectTaskUid,
                String microsoftProjectTaskId,
                String taskName,
                boolean leafTask
        ) {
            this.importedTaskId = importedTaskId;
            this.microsoftProjectTaskUid = microsoftProjectTaskUid;
            this.microsoftProjectTaskId = microsoftProjectTaskId;
            this.taskName = taskName;
            this.leafTask = leafTask;
        }

        private static ExportTaskBuilder fromLine(ExportPreviewLineRecord line) {
            requireText(line.importedTaskExternalUid(), "Imported task external UID is required for export artifacts.");
            requireText(line.importedTaskExternalId(), "Imported task external ID is required for export artifacts.");
            return new ExportTaskBuilder(
                    line.importedTaskId(),
                    line.importedTaskExternalUid(),
                    line.importedTaskExternalId(),
                    line.importedTaskName() == null || line.importedTaskName().isBlank()
                            ? line.importedTaskId().toString()
                            : line.importedTaskName(),
                    line.leafTask()
            );
        }

        private void addFieldValue(ProjectExportArtifactFieldValue fieldValue) {
            fieldValues.add(fieldValue);
        }

        private ProjectExportArtifactTask build() {
            return new ProjectExportArtifactTask(
                    importedTaskId.toString(),
                    microsoftProjectTaskUid,
                    microsoftProjectTaskId,
                    taskName,
                    leafTask,
                    fieldValues
            );
        }

        private static void requireText(String value, String message) {
            if (value == null || value.isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, message);
            }
        }
    }
}
