package com.shutdowntracker.projectexport.contract;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ProjectExportArtifactRequest(
        String projectName,
        List<ProjectExportArtifactTask> tasks
) {
    public ProjectExportArtifactRequest {
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("projectName is required.");
        }
        tasks = List.copyOf(tasks == null ? List.of() : tasks);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("At least one export task is required.");
        }
        requireUniqueCandidates(tasks);
        requireConsistentTaskIdentity(tasks);
    }

    private static void requireConsistentTaskIdentity(List<ProjectExportArtifactTask> tasks) {
        Set<String> importedTaskIds = new HashSet<>();
        Set<String> projectTaskUids = new HashSet<>();
        Set<String> projectTaskIds = new HashSet<>();
        for (ProjectExportArtifactTask task : tasks) {
            if (!importedTaskIds.add(task.importedTaskId())) {
                throw new IllegalArgumentException(
                        "Each importedTaskId must map to exactly one worker task: '" + task.importedTaskId() + "'."
                );
            }
            if (!projectTaskUids.add(task.microsoftProjectTaskUid())) {
                throw new IllegalArgumentException(
                        "Each Microsoft Project task UID must map to exactly one imported task: '"
                                + task.microsoftProjectTaskUid()
                                + "'."
                );
            }
            if (!projectTaskIds.add(task.microsoftProjectTaskId())) {
                throw new IllegalArgumentException(
                        "Each Microsoft Project task ID must map to exactly one imported task: '"
                                + task.microsoftProjectTaskId()
                                + "'."
                );
            }
        }
    }

    private static void requireUniqueCandidates(List<ProjectExportArtifactTask> tasks) {
        Set<CandidateKey> candidates = new HashSet<>();
        for (ProjectExportArtifactTask task : tasks) {
            for (ProjectExportArtifactFieldValue fieldValue : task.fieldValues()) {
                CandidateKey candidate = new CandidateKey(task.importedTaskId(), fieldValue.field());
                if (!candidates.add(candidate)) {
                    throw new IllegalArgumentException(
                            "Duplicate export artifact candidate for importedTaskId '"
                                    + task.importedTaskId()
                                    + "' and field '"
                                    + fieldValue.field().fieldName()
                                    + "'."
                    );
                }
            }
        }
    }

    private record CandidateKey(String importedTaskId, ProjectExportArtifactField field) {
    }
}
