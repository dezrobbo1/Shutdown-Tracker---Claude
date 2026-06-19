package com.shutdowntracker.api.project;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ReviewProjectBootstrapService {

    private final ProjectRepository projectRepository;
    private final ReviewProjectBootstrapProperties properties;

    public ReviewProjectBootstrapService(
            ProjectRepository projectRepository,
            ReviewProjectBootstrapProperties properties
    ) {
        this.projectRepository = projectRepository;
        this.properties = properties;
    }

    public ProjectRecord ensureReviewProject() {
        return projectRepository.findReviewBootstrapProject(properties.projectName())
                .orElseGet(() -> projectRepository.createReviewBootstrapProject(new ReviewProjectCreateRequest(
                        properties.projectName(),
                        properties.description(),
                        properties.timezone()
                )));
    }
}
