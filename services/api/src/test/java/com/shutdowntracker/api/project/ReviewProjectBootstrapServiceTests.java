package com.shutdowntracker.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewProjectBootstrapServiceTests {

    @Test
    void returnsExistingReviewProjectWhenPresent() {
        ProjectRecord existing = new ProjectRecord(UUID.randomUUID(), "Synthetic Review Project", "active", "UTC");
        FakeProjectRepository repository = new FakeProjectRepository(Optional.of(existing));
        ReviewProjectBootstrapService service = new ReviewProjectBootstrapService(repository, defaultProperties());

        ProjectRecord project = service.ensureReviewProject();

        assertThat(project).isEqualTo(existing);
        assertThat(repository.createRequest).isNull();
    }

    @Test
    void createsSyntheticReviewProjectWhenMissing() {
        FakeProjectRepository repository = new FakeProjectRepository(Optional.empty());
        ReviewProjectBootstrapService service = new ReviewProjectBootstrapService(repository, defaultProperties());

        ProjectRecord project = service.ensureReviewProject();

        assertThat(project.name()).isEqualTo("Synthetic Review Project");
        assertThat(project.status()).isEqualTo("active");
        assertThat(project.timezone()).isEqualTo("UTC");
        assertThat(repository.createRequest.description())
                .contains("Synthetic review project");
    }

    private ReviewProjectBootstrapProperties defaultProperties() {
        return new ReviewProjectBootstrapProperties(
                false,
                "Synthetic Review Project",
                "Synthetic review project for local and review-environment setup only.",
                "UTC"
        );
    }

    private static class FakeProjectRepository implements ProjectRepository {

        private final Optional<ProjectRecord> existingProject;
        private ReviewProjectCreateRequest createRequest;

        private FakeProjectRepository(Optional<ProjectRecord> existingProject) {
            this.existingProject = existingProject;
        }

        @Override
        public Optional<ProjectRecord> findReviewBootstrapProject(String projectName) {
            return existingProject.filter(project -> project.name().equals(projectName));
        }

        @Override
        public ProjectRecord createReviewBootstrapProject(ReviewProjectCreateRequest request) {
            createRequest = request;
            return new ProjectRecord(UUID.randomUUID(), request.name(), "active", request.timezone());
        }
    }
}
