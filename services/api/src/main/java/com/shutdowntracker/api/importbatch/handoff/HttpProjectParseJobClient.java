package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.project-parse-worker", name = "enabled", havingValue = "true")
public class HttpProjectParseJobClient implements ProjectParseJobClient {

    private final RestClient restClient;
    private final String parseSummaryPath;

    public HttpProjectParseJobClient(
            RestClient.Builder restClientBuilder,
            ProjectParseWorkerClientProperties properties
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
        this.parseSummaryPath = properties.parseSummaryPath();
    }

    @Override
    public ProjectParseSummaryResponse requestParseSummary(ProjectParseSummaryRequest request) {
        Objects.requireNonNull(request, "request is required.");
        return restClient.post()
                .uri(parseSummaryPath)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProjectParseSummaryResponse.class);
    }
}
