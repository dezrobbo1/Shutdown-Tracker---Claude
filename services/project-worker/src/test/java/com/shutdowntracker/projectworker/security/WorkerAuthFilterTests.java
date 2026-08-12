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
    void allowsRequestCarryingTheConfiguredSharedSecret() throws ServletException, IOException {
        MockHttpServletRequest request = workerRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        new WorkerAuthFilter(SECRET).doFilter(request, response, chain);

        assertThat(chain.called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsRequestWithoutAnAuthorizationHeader() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        new WorkerAuthFilter(SECRET).doFilter(workerRequest(), response, chain);

        assertThat(chain.called).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("unauthorized");
    }

    @Test
    void rejectsRequestCarryingTheWrongSecret() throws ServletException, IOException {
        MockHttpServletRequest request = workerRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-the-configured-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        new WorkerAuthFilter(SECRET).doFilter(request, response, chain);

        assertThat(chain.called).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void failsClosedWhenAuthenticationIsEnabledWithoutASecret() {
        WorkerSecurityConfiguration configuration = new WorkerSecurityConfiguration();

        assertThatThrownBy(() -> configuration.workerAuthFilterRegistration(
                new WorkerAuthProperties(true, null)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shared-secret must be set");
    }

    @Test
    void allowsAuthenticationToBeDisabledForIsolatedLocalDevelopment() {
        WorkerSecurityConfiguration configuration = new WorkerSecurityConfiguration();

        assertThat(configuration.workerAuthFilterRegistration(new WorkerAuthProperties(false, null)).isEnabled())
                .isFalse();
    }

    @Test
    void treatsBlankSecretAsMissing() {
        assertThat(new WorkerAuthProperties(true, "   ").sharedSecret()).isNull();
    }

    @Test
    void enablesAuthenticationByDefault() {
        assertThat(new WorkerAuthProperties(null, SECRET).isEnabled()).isTrue();
    }

    private MockHttpServletRequest workerRequest() {
        return new MockHttpServletRequest("POST", "/worker/project-import/parse-summary");
    }

    private static class RecordingFilterChain implements FilterChain {

        private boolean called;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            this.called = true;
        }
    }
}
