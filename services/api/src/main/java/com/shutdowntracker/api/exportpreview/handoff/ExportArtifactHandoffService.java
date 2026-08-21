package com.shutdowntracker.api.exportpreview.handoff;

import com.shutdowntracker.api.exportpreview.ExportBatchGeneratedRequest;
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
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSource;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ExportArtifactHandoffService {

    private static final String GENERATED_MESSAGE = "Worker-generated MSPDI/XML artifact metadata recorded. "
            + "No Microsoft Project write-back was run.";
    private static final String WORKER_PROJECT_NAME_PREFIX = "Shutdown Tracker Export Batch ";

    /**
     * A candidate is produced by applying approved inputs to the accepted source schedule, and
     * Microsoft Project can only be handed MSPDI/XML back. Deriving a candidate from a native
     * {@code .mpp} would therefore mean converting formats in both directions, which can silently
     * drop links, calendars or constraints from a file that still looks like a schedule.
     * {@code .mpp} remains supported for import and reporting.
     */
    private static final String MSPDI_SOURCE_KIND = "mspdi_xml";

    /**
     * The artifact this batch generated, and an open stream over its bytes.
     *
     * <p>Reading it back is not a second kind of act from generating it: it is the same fact,
     * fetched instead of made. So it lives beside generation and is authorised the same way.
     */
    public record ExportArtifactContent(UUID exportBatchId, String filename, InputStream content) {
    }

    private final ExportPreviewService exportPreviewService;
    private final ProjectExportArtifactJobClient exportArtifactJobClient;
    private final ExportArtifactStorage exportArtifactStorage;
    private final AcceptedSourceFileRepository acceptedSourceFileRepository;

    public ExportArtifactHandoffService(
            ExportPreviewService exportPreviewService,
            ProjectExportArtifactJobClient exportArtifactJobClient,
            ExportArtifactStorage exportArtifactStorage,
            AcceptedSourceFileRepository acceptedSourceFileRepository
    ) {
        this.exportPreviewService = exportPreviewService;
        this.exportArtifactJobClient = exportArtifactJobClient;
        this.exportArtifactStorage = exportArtifactStorage;
        this.acceptedSourceFileRepository = acceptedSourceFileRepository;
    }

    @Transactional
    public ExportArtifactGenerationResponse generateArtifact(
            UUID projectId,
            UUID exportBatchId,
            ExportArtifactGenerationRequest request
    ) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = Objects.requireNonNull(exportBatchId, "exportBatchId is required.");
        ExportArtifactGenerationRequest requiredRequest = request == null
                ? ExportArtifactGenerationRequest.empty()
                : request;

        ExportPreviewDetail approvedPreview = exportPreviewService.getApprovedPreviewForArtifactGeneration(
                requiredProjectId,
                requiredExportBatchId
        );

        ExportArtifactStorageLocation storageLocation =
                exportArtifactStorage.prepareExportArtifact(requiredProjectId, requiredExportBatchId);
        ProjectExportArtifactGenerationRequest workerRequest = buildWorkerRequest(approvedPreview, storageLocation);
        ProjectExportArtifactGenerationResponse workerResponse =
                exportArtifactJobClient.generateArtifact(workerRequest);
        verifyWorkerResponse(requiredProjectId, requiredExportBatchId, storageLocation, workerResponse);

        ExportPreviewDetail generatedPreview = exportPreviewService.markGenerated(
                requiredProjectId,
                requiredExportBatchId,
                new ExportBatchGeneratedRequest(
                        workerResponse.exportFileUri(),
                        workerResponse.exportFileHash(),
                        requiredRequest.generatedByUserId(),
                        reason(requiredRequest),
                        requiredRequest.metadata(),
                        generationProvenance(storageLocation, workerResponse)
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
                        resolveAcceptedSource(preview),
                        taskBuilders.values().stream()
                                .map(ExportTaskBuilder::build)
                                .toList()
                )
        );
    }

    /**
     * Resolves the schedule this candidate must be derived from, and refuses to proceed rather than
     * generating a candidate whose provenance cannot be stated exactly.
     */
    private ProjectExportArtifactSource resolveAcceptedSource(ExportPreviewDetail preview) {
        AcceptedSourceFile sourceFile = acceptedSourceFileRepository
                .findByProjectSnapshotId(preview.batch().projectSnapshotId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Accepted snapshot has no source file, so no candidate schedule can be derived from it."
                ));

        if (!MSPDI_SOURCE_KIND.equalsIgnoreCase(sourceFile.fileKind())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Candidate schedule generation requires an MSPDI/XML source schedule; this snapshot was "
                            + "imported from '" + sourceFile.fileKind() + "'. Re-import the project from XML "
                            + "saved by Microsoft Project."
            );
        }

        if (sourceFile.contentHash() == null || sourceFile.contentHash().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Accepted source schedule has no recorded content hash, so the candidate could not be "
                            + "proven to derive from the reviewed schedule."
            );
        }

        return new ProjectExportArtifactSource(
                sourceFile.sourceFileId(),
                sourceFile.storageUri(),
                sourceFile.contentHash()
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

    private Map<String, Object> generationProvenance(
            ExportArtifactStorageLocation storageLocation,
            ProjectExportArtifactGenerationResponse workerResponse
    ) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("workerArtifactGenerated", true);
        provenance.put("projectWriteBack", false);
        provenance.put("artifactStorageManagedByApi", true);
        provenance.put("artifactStorageKind", storageLocation.storageKind());
        provenance.put("artifactStorageUri", storageLocation.storageUri());
        provenance.put("artifactFilename", storageLocation.artifactFilename());
        provenance.put("artifactFormat", workerResponse.artifactSummary().artifactFormat());
        provenance.put("artifactTaskCount", workerResponse.artifactSummary().taskCount());
        provenance.put("artifactExportedFieldCount", workerResponse.artifactSummary().exportedFieldCount());
        provenance.put("artifactSizeBytes", workerResponse.artifactSummary().sizeBytes());
        provenance.put("workerMessage", workerResponse.message());
        return provenance;
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

    /**
     * Opens the generated artifact for a batch. The caller closes the stream.
     *
     * <p>A missing file is a 409 rather than a 500, for the same reason the returned-candidate
     * endpoint gives: the row saying an artifact was generated stays true even when the store has
     * since lost the bytes. Reporting that as a server fault would say the wrong thing about which
     * of the two is wrong.
     */
    public ExportArtifactContent artifactContent(UUID projectId, UUID exportBatchId) {
        ExportPreviewDetail detail = exportPreviewService.getPreview(projectId, exportBatchId);
        String storageUri = detail.batch().exportFileUri();

        if (storageUri == null || storageUri.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This export batch has not generated an artifact, so there is nothing to download.");
        }

        String filename = filenameOf(storageUri, exportBatchId);

        try {
            return new ExportArtifactContent(
                    exportBatchId, filename, exportArtifactStorage.read(storageUri));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The artifact generated as '" + filename + "' is recorded but its file could not be read.",
                    exception);
        }
    }

    /**
     * The stored filename, taken from the last segment of the URI the generation recorded.
     *
     * <p>Falls back to the batch id rather than failing: the name is what a planner's browser will
     * save the file as, and a download that arrives with an awkward name is better than a download
     * refused because the name was.
     */
    private static String filenameOf(String storageUri, UUID exportBatchId) {
        String fallback = exportBatchId + ".mspdi.xml";
        try {
            String path = new URI(storageUri).getPath();
            if (path == null) {
                return fallback;
            }
            String candidate = path.substring(path.lastIndexOf('/') + 1);
            return candidate.isBlank() ? fallback : candidate;
        } catch (URISyntaxException exception) {
            return fallback;
        }
    }
}
