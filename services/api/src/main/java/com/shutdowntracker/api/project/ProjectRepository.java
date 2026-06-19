package com.shutdowntracker.api.project;

import java.util.Optional;

public interface ProjectRepository {

    Optional<ProjectRecord> findReviewBootstrapProject(String projectName);

    ProjectRecord createReviewBootstrapProject(ReviewProjectCreateRequest request);
}
