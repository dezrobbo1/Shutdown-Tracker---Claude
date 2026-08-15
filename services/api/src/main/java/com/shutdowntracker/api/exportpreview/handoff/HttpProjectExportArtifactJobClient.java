package com.shutdowntracker.api.exportpreview.handoff;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.project-export-worker", name = "enabled", havingValue = "true")
public class HttpProjectExportArtifactJobClient implements ProjectExportArtifactJobClient {

    private final RestClient restClient;
    private final String generateArtifactPath;

    public HttpProjectExportArtifactJobClient(
            RestClient.Builder restClientBuilder,
            ProjectExportWorkerClientProperties properties
    ) {
        RestClient.Builder builder = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(properties.connectTimeout())
                        .withReadTimeout(properties.readTimeout())));
        if (properties.sharedSecret() != null) {
            builder = builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.sharedSecret());
        }
        this.restClient = builder.build();
        this.generateArtifactPath = properties.generateArtifactPath();
    }

    @Override
    public ProjectExportArtifactGenerationResponse generateArtifact(ProjectExportArtifactGenerationRequest request) {
        Objects.requireNonNull(request, "request is required.");
        return restClient.post()
                .uri(generateArtifactPath)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProjectExportArtifactGenerationResponse.class);
    }
}
