package com.shutdowntracker.projectworker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WorkerAuthFilterTests {

    private static final String SECRET = "synthetic-worker-secret";

    @Test
    void allowsConfiguredSecret() throws ServletException, IOException {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();
        new WorkerAuthFilter(SECRET).doFilter(request, response, chain);
        assertThat(chain.called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMissingOrWrongSecret() throws ServletException, IOException {
        MockHttpServletResponse missing = new MockHttpServletResponse();
        RecordingFilterChain missingChain = new RecordingFilterChain();
        new WorkerAuthFilter(SECRET).doFilter(request(), missing, missingChain);
        assertThat(missingChain.called).isFalse();
        assertThat(missing.getStatus()).isEqualTo(401);

        MockHttpServletRequest wrongRequest = request();
        wrongRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer wrong");
        MockHttpServletResponse wrong = new MockHttpServletResponse();
        RecordingFilterChain wrongChain = new RecordingFilterChain();
        new WorkerAuthFilter(SECRET).doFilter(wrongRequest, wrong, wrongChain);
        assertThat(wrongChain.called).isFalse();
        assertThat(wrong.getStatus()).isEqualTo(401);
    }

    @Test
    void failsClosedWhenEnabledWithoutSecret() {
        WorkerSecurityConfiguration configuration = new WorkerSecurityConfiguration();
        assertThatThrownBy(() -> configuration.workerAuthFilterRegistration(new WorkerAuthProperties(true, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shared-secret must be set");
    }

    @Test
    void permitsExplicitDisableForIsolatedTests() {
        assertThat(new WorkerSecurityConfiguration()
                .workerAuthFilterRegistration(new WorkerAuthProperties(false, null)).isEnabled()).isFalse();
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/worker/project-import/parse-summary");
    }

    private static class RecordingFilterChain implements FilterChain {
        private boolean called;
        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            called = true;
        }
    }
}
