package com.shutdowntracker.api.candidate;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.candidate.storage.CandidateScheduleStorage;
import com.shutdowntracker.api.candidate.storage.CandidateScheduleStorageProperties;
import com.shutdowntracker.api.candidate.storage.CandidateScheduleStorageRequest;
import com.shutdowntracker.api.candidate.storage.StoredCandidateSchedule;
import com.shutdowntracker.api.exportpreview.ExportBatchState;
import com.shutdowntracker.api.storage.Sha256;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Records the schedule Microsoft Project calculated, once a planner brings it back.
 *
 * <p>Shutdown Tracker generates a candidate and proves it wrote nothing into it but the approved
 * execution inputs. Microsoft Project then recalculates that candidate, and until now the result
 * existed only on the planner's machine: nothing could be compared against the source, classified,
 * or decided about. A returned candidate is the first fact in that chain.
 *
 * <p>What this does <em>not</em> do is as important. It does not parse the schedule, read values
 * out of it, or draw any conclusion from it. It stores the bytes, hashes them, and binds them to
 * the export batch and accepted source they must have come from. Saying what changed is the
 * delta's job, and the delta belongs to the project worker, where Project processing lives.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class CandidateScheduleRunService {

    /**
     * The batch states in which Microsoft Project can have been handed an artifact.
     *
     * <p>{@code generated} is enough: the artifact exists, and a planner may open it without
     * Shutdown Tracker having recorded that they did. {@code verified} is included because
     * confirming that an artifact opened correctly and returning what Project made of it are two
     * separate acts, and a planner may well do them in that order. The states before generation are
     * refused because there is no artifact yet, and a candidate returned against one of them could
     * not have come from this batch.
     */
    private static final Set<ExportBatchState> RETURNABLE_AGAINST = Set.of(
            ExportBatchState.GENERATED,
            ExportBatchState.OPENED_IN_MICROSOFT_PROJECT,
            ExportBatchState.VERIFIED);

    private static final String MSPDI_ROOT_ELEMENT = "Project";

    private final CandidateScheduleRunRepository repository;
    private final CandidateScheduleStorage storage;
    private final CandidateScheduleStorageProperties storageProperties;
    private final AuditEventRecorder auditEventRecorder;

    public CandidateScheduleRunService(
            CandidateScheduleRunRepository repository,
            CandidateScheduleStorage storage,
            CandidateScheduleStorageProperties storageProperties,
            AuditEventRecorder auditEventRecorder
    ) {
        this.repository = repository;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.auditEventRecorder = auditEventRecorder;
    }

    /**
     * Records a candidate schedule returned against one export batch.
     *
     * <p>Returning the same bytes against the same batch twice resolves to the run the first upload
     * created rather than recording a second calculation, and is not audited again: the candidate
     * came back once, and an audit trail saying otherwise would be wrong. The same rule the offline
     * queue relies on for progress and problems, for the same reason.
     */
    @Transactional
    public CandidateScheduleRunRecord returnCandidate(
            UUID projectId,
            UUID exportBatchId,
            Actor actor,
            MultipartFile file,
            String microsoftProjectVersion,
            String plannerNote
    ) {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(exportBatchId, "exportBatchId is required.");
        Objects.requireNonNull(actor, "actor is required.");
        Objects.requireNonNull(file, "file is required.");

        CandidateScheduleRunRepository.ExportBatchForReturn batch = requireReturnableBatch(projectId, exportBatchId);
        CandidateScheduleRunRepository.AcceptedSource source = requireAcceptedSource(projectId, batch);

        requireAcceptableUpload(file);

        // Hashed before it is stored, so a re-upload of a candidate this batch already holds costs
        // one read rather than a second copy of a schedule that can run to hundreds of megabytes.
        String contentHash = hash(file);
        var existing = repository.findByContentHash(projectId, exportBatchId, contentHash);
        if (existing.isPresent()) {
            return existing.get();
        }

        StoredCandidateSchedule stored = store(file);

        CandidateScheduleRunRecord run;
        try {
            run = repository.create(new CandidateScheduleRunRepository.NewCandidateScheduleRun(
                    projectId,
                    exportBatchId,
                    batch.projectSnapshotId(),
                    source.sourceFileId(),
                    source.contentHash(),
                    batch.exportFileHash(),
                    stored.originalFilename(),
                    stored.storageUri(),
                    stored.contentHashSha256(),
                    stored.sizeBytes(),
                    trimmedOrNull(microsoftProjectVersion),
                    trimmedOrNull(plannerNote),
                    actor.userId()));
        } catch (DuplicateKeyException exception) {
            // Two uploads of the same bytes at once. The unique index decides, not the read above.
            // The loser cannot resolve to the winner's run from here — the failed statement has
            // already aborted this transaction — so it says what happened and leaves retrying to
            // the caller, where the read above will find the run that won. What it stored is a
            // second copy of a file the store already holds; nothing points at it, and deleting a
            // candidate schedule is not something this service may do on its own.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This candidate schedule is already being recorded against the export batch. "
                            + "Returning it again will resolve to the run that was created.",
                    exception);
        }

        auditEventRecorder.record(returnedAuditEvent(actor, run));
        return run;
    }

    public List<CandidateScheduleRunRecord> runsForExportBatch(UUID projectId, UUID exportBatchId) {
        return repository.findForExportBatch(projectId, exportBatchId);
    }

    public List<CandidateScheduleRunRecord> runsForProject(UUID projectId) {
        return repository.findForProject(projectId);
    }

    public CandidateScheduleRunRecord run(UUID projectId, UUID runId) {
        return repository.find(projectId, runId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate schedule run not found."));
    }

    /**
     * One returned candidate and its stored bytes. The caller closes the stream.
     *
     * <p>The run travels with the stream because serving the file needs its name and size, and
     * reading the row twice to get them would be two queries for one answer.
     *
     * <p>A run whose file cannot be read is reported as exactly that. The row is evidence that a
     * candidate was returned, and it stays true even when the store behind it has lost the file.
     */
    public CandidateScheduleContent content(UUID projectId, UUID runId) {
        CandidateScheduleRunRecord run = run(projectId, runId);
        String storageUri = repository.findStorageUri(projectId, runId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate schedule run not found."));
        try {
            return new CandidateScheduleContent(run, storage.read(storageUri));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The candidate schedule returned as '" + run.candidateOriginalFilename()
                            + "' is recorded but its file could not be read.",
                    exception);
        }
    }

    /** A returned candidate, and an open stream over the bytes that were stored for it. */
    public record CandidateScheduleContent(CandidateScheduleRunRecord run, InputStream content) {
    }

    private CandidateScheduleRunRepository.ExportBatchForReturn requireReturnableBatch(
            UUID projectId, UUID exportBatchId) {
        CandidateScheduleRunRepository.ExportBatchForReturn batch = repository
                .findExportBatch(projectId, exportBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export batch not found."));

        if (!RETURNABLE_AGAINST.contains(batch.state())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This export batch is " + batch.state().databaseValue()
                            + ", so Microsoft Project was never handed a candidate schedule from it.");
        }
        return batch;
    }

    private CandidateScheduleRunRepository.AcceptedSource requireAcceptedSource(
            UUID projectId, CandidateScheduleRunRepository.ExportBatchForReturn batch) {
        CandidateScheduleRunRepository.AcceptedSource source = repository
                .findAcceptedSource(projectId, batch.projectSnapshotId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The accepted source schedule behind this export batch could not be resolved."));

        // The same requirement candidate generation makes. A candidate reviewed against a source
        // that cannot be identified would classify every difference confidently and wrongly.
        if (source.contentHash() == null || source.contentHash().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Accepted source schedule has no recorded content hash, so a returned candidate could not "
                            + "be bound to the reviewed schedule.");
        }
        return source;
    }

    /**
     * What can be said about the upload before anything has parsed it.
     *
     * <p>The root element check is not schedule validation and does not claim the file is this
     * batch's candidate — only the delta can say that. It is here so a planner who uploads the
     * wrong file is told immediately, rather than at review time, and so obviously-not-a-schedule
     * bytes never reach the store.
     */
    private void requireAcceptableUpload(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A returned candidate needs a filename.");
        }
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A returned candidate must be the MSPDI/XML Microsoft Project saved, not '" + filename + "'.");
        }
        if (file.getSize() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The returned candidate file is empty.");
        }
        if (file.getSize() > storageProperties.maxSizeBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "The returned candidate exceeds the " + storageProperties.maxSizeBytes() + " byte limit.");
        }
        if (!MSPDI_ROOT_ELEMENT.equals(rootElementName(file))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The returned candidate is not an MSPDI/XML document: its root element is not <Project>.");
        }
    }

    /**
     * The first element in the document, read without building one.
     *
     * <p>External entities and DTDs are off: the file arrives from outside, and a parser that
     * resolves what a document tells it to resolve is a way to read this server's filesystem.
     */
    private String rootElementName(MultipartFile file) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        XMLStreamReader reader = null;
        try (InputStream content = file.getInputStream()) {
            reader = factory.createXMLStreamReader(content);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    return reader.getLocalName();
                }
            }
            return null;
        } catch (XMLStreamException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The returned candidate is not readable as XML.", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read the returned candidate schedule.", exception);
        } finally {
            closeQuietly(reader);
        }
    }

    private String hash(MultipartFile file) {
        try {
            return Sha256.hex(file.getInputStream());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read the returned candidate schedule.", exception);
        }
    }

    private StoredCandidateSchedule store(MultipartFile file) {
        try {
            return storage.store(new CandidateScheduleStorageRequest(
                    file.getOriginalFilename(), file.getInputStream(), file.getSize()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to store the returned candidate schedule.", exception);
        }
    }

    private AuditEventCreateRequest returnedAuditEvent(Actor actor, CandidateScheduleRunRecord run) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("candidateScheduleRunId", run.id().toString());
        metadata.put("exportBatchId", run.exportBatchId().toString());
        metadata.put("acceptedSourceFileHash", run.acceptedSourceFileHash());
        metadata.put("candidateContentHash", run.candidateContentHash());
        metadata.put("candidateSizeBytes", run.candidateSizeBytes());
        if (run.generatedArtifactHash() != null) {
            metadata.put("generatedArtifactHash", run.generatedArtifactHash());
        }
        if (run.microsoftProjectVersion() != null) {
            metadata.put("microsoftProjectVersion", run.microsoftProjectVersion());
        }
        // Three claims the state model insists are kept apart. A returned candidate is only the
        // first of them, and the audit row says so rather than leaving a reader to assume.
        metadata.put("deltaComputed", false);
        metadata.put("plannerDecision", false);
        metadata.put("masterAdopted", false);

        // Category `export`, not `approval`: returning a candidate records what Microsoft Project
        // calculated and approves nothing. The candidate handoff is part of the export lifecycle,
        // and filing it under approval would claim a decision that has not been made.
        return AuditEventCreateRequest.userEvent(
                run.projectId(),
                actor.userId(),
                actor.displayName(),
                actor.role(),
                AuditEventCategory.EXPORT,
                AuditEventTypes.CANDIDATE_SCHEDULE_RETURNED,
                "candidate_schedule_run",
                run.id(),
                run.candidateOriginalFilename(),
                Map.of(),
                Map.of("state", run.state().databaseValue()),
                "Candidate schedule produced by Microsoft Project returned for review.",
                run.projectSnapshotId(),
                run.exportBatchId(),
                metadata);
    }

    private static String trimmedOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // The stream this read from is closed by its own try-with-resources.
        }
    }
}
