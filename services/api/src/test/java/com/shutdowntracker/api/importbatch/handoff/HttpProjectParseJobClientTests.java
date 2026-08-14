package com.shutdowntracker.api.importbatch.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

class HttpProjectParseJobClientTests {

    private HttpServer server;
    private String baseUrl;

    /** Released during teardown so the stalled handler returns instead of holding the test open. */
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void startUnresponsiveWorker() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/worker/project-import/parse-summary", exchange -> {
            try {
                // Simulate a worker that accepts the connection and then stops responding.
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopUnresponsiveWorker() {
        release.countDown();
        server.stop(0);
    }

    @Test
    void failsFastWhenTheWorkerStopsResponding() {
        HttpProjectParseJobClient client = new HttpProjectParseJobClient(
                RestClient.builder(),
                new ProjectParseWorkerClientProperties(
                        baseUrl,
                        null,
                        null,
                        null,
                        Duration.ofSeconds(2),
                        Duration.ofMillis(400)
                )
        );
        ProjectParseSummaryRequest request = new ProjectParseSummaryRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "file:///synthetic/source/synthetic-basic-wbs.mspdi.xml",
                "synthetic-basic-wbs.mspdi.xml"
        );

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> client.requestParseSummary(request))
                .isInstanceOf(RestClientException.class);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // Without a configured read timeout this call blocks for as long as the worker stays silent.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }
}
