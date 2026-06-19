package com.shutdowntracker.api.exportpreview.handoff;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "shutdown-tracker.project-export-worker",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DisconnectedProjectExportArtifactJobClient implements ProjectExportArtifactJobClient {

    @Override
    public ProjectExportArtifactGenerationResponse generateArtifact(ProjectExportArtifactGenerationRequest request) {
        Objects.requireNonNull(request, "request is required.");
        throw new UnsupportedOperationException(
                "Project export artifact handoff is not connected yet. MSPDI/XML artifact generation remains in "
                        + "services/project-worker; the API does not generate files or write back to Microsoft Project."
        );
    }
}
