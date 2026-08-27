package com.shutdowntracker.api.reviewreset;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Clears a synthetic review project so the round-trip trial can be walked again from nothing.
 *
 * <p>Four independent gates stand in front of the deletion, and they are independent on purpose —
 * each one alone would be enough to regret.
 *
 * <ol>
 *   <li><strong>The bean is conditional.</strong> Without both flags this controller does not exist
 *       and the URL is an ordinary 404. A flag checked inside the method would leave the route
 *       mapped and is one refactor from being read in the wrong branch.</li>
 *   <li><strong>The capability is checked</strong> against stored membership, so the actor header
 *       cannot talk its way in.</li>
 *   <li><strong>The project must carry the synthetic marker.</strong> This is the one that matters:
 *       even with the flags on and an administrator acting, real project data is unreachable.</li>
 *   <li><strong>The project's name must be typed back.</strong> Verified here rather than only in
 *       the browser, because this endpoint is reachable by anything that can make a request.</li>
 * </ol>
 *
 * <p>POST rather than DELETE. No production controller in this service maps DELETE, and the client's
 * {@code ReviewApiSurface} types its method as GET or POST; widening that for one route would cost
 * more than the verb is worth.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/review-reset")
@ConditionalOnProperty(
        name = {
                "shutdown-tracker.review-data-reset.enabled",
                "shutdown-tracker.persistence.enabled"
        },
        havingValue = "true")
public class ReviewDataResetController {

    private final ProjectAuthorizationService authorization;
    private final ReviewResetProjectGuard projectGuard;
    private final ReviewDataResetService resetService;
    private final ReviewDataResetBlobCleaner blobCleaner;

    public ReviewDataResetController(
            ProjectAuthorizationService authorization,
            ReviewResetProjectGuard projectGuard,
            ReviewDataResetService resetService,
            ReviewDataResetBlobCleaner blobCleaner
    ) {
        this.authorization = authorization;
        this.projectGuard = projectGuard;
        this.resetService = resetService;
        this.blobCleaner = blobCleaner;
    }

    @PostMapping
    public ReviewDataResetResult reset(
            @PathVariable UUID projectId,
            Actor actor,
            @RequestBody ReviewDataResetRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.RESET_REVIEW_DATA);

        String projectName = projectGuard.requireSyntheticReviewProject(projectId);
        requireConfirmation(request, projectName);

        List<ReviewDataResetResult.TableReset> tables = resetService.reset(projectId, projectName, actor);

        // Past this point the database is already committed. Nothing below may throw, or a caller
        // would read a failure and reasonably conclude the reset did not happen.
        List<ReviewDataResetResult.BlobReset> blobs = blobCleaner.clear();
        List<String> warnings = new ArrayList<>();

        List<ReviewDataResetResult.BlobReset> failures = blobs.stream()
                .filter(blob -> blob.error() != null)
                .toList();
        if (!failures.isEmpty()) {
            resetService.recordIncompleteBlobCleanup(projectId, projectName, actor, failures);
            warnings.add("The database was cleared but some stored files were not. "
                    + "Nothing refers to them; they are wasted disk, not broken state.");
        }
        warnings.add("Reports already queued on a field device refer to tasks that no longer exist "
                + "and will fail to send. Clear site data on the device.");

        return new ReviewDataResetResult(
                projectId,
                projectName,
                OffsetDateTime.now(ZoneOffset.UTC),
                tables,
                blobs,
                ReviewDataResetScope.KEEP,
                List.copyOf(warnings));
    }

    private static void requireConfirmation(ReviewDataResetRequest request, String projectName) {
        String typed = request == null || request.confirmation() == null ? "" : request.confirmation().trim();
        if (!typed.equals(projectName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Type the project's name exactly to confirm. Expected \"" + projectName + "\".");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @org.springframework.web.bind.annotation.ExceptionHandler(ReviewResetRefusedException.class)
    public String refused(ReviewResetRefusedException e) {
        return e.getMessage();
    }
}
