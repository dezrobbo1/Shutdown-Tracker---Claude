package com.shutdowntracker.projectworker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects worker handoff requests that do not present the configured shared secret.
 *
 * <p>Registered only for the worker handoff paths. Actuator health and info remain reachable so that
 * deployment probes do not need the credential.
 */
public class WorkerAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHORIZED_BODY =
            "{\"error\":\"unauthorized\",\"message\":\"Worker handoff requires a valid shared secret.\"}";

    private final byte[] expectedSecret;

    public WorkerAuthFilter(String sharedSecret) {
        this.expectedSecret = sharedSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isAuthorized(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(UNAUTHORIZED_BODY);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthorized(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }

        byte[] presented = authorizationHeader.substring(BEARER_PREFIX.length())
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedSecret, presented);
    }
}
