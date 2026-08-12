package com.shutdowntracker.api.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedHeaderActorResolverTests {

    private static final String ACTOR_ID = "00000000-0000-0000-0000-0000000000a1";

    @Test
    void resolvesActorFromTrustedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects/x/export-preview");
        request.addHeader("X-Shutdown-Tracker-Actor-Id", ACTOR_ID);
        request.addHeader("X-Shutdown-Tracker-Actor-Role", "planner");
        request.addHeader("X-Shutdown-Tracker-Actor-Name", "Synthetic Planner");

        Actor actor = resolver(true).resolve(request);

        assertThat(actor.userId()).isEqualTo(UUID.fromString(ACTOR_ID));
        assertThat(actor.role()).isEqualTo("planner");
        assertThat(actor.displayName()).isEqualTo("Synthetic Planner");
    }

    @Test
    void failsClosedWhenActorResolutionIsNotConfigured() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Shutdown-Tracker-Actor-Id", ACTOR_ID);

        assertThatThrownBy(() -> resolver(false).resolve(request))
                .isInstanceOf(UnauthenticatedRequestException.class)
                .hasMessageContaining("Actor resolution is not configured");
    }

    @Test
    void rejectsRequestWithoutAnActorHeader() {
        assertThatThrownBy(() -> resolver(true).resolve(new MockHttpServletRequest()))
                .isInstanceOf(UnauthenticatedRequestException.class)
                .hasMessageContaining("missing an authenticated actor");
    }

    @Test
    void rejectsActorHeaderThatIsNotAUuid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Shutdown-Tracker-Actor-Id", "not-a-uuid");

        assertThatThrownBy(() -> resolver(true).resolve(request))
                .isInstanceOf(UnauthenticatedRequestException.class)
                .hasMessageContaining("not a valid UUID");
    }

    @Test
    void reportsUnauthorizedStatus() {
        assertThat(new UnauthenticatedRequestException("nope").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void treatsBlankOptionalHeadersAsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Shutdown-Tracker-Actor-Id", ACTOR_ID);
        request.addHeader("X-Shutdown-Tracker-Actor-Role", "   ");

        Actor actor = resolver(true).resolve(request);

        assertThat(actor.role()).isNull();
        assertThat(actor.displayName()).isNull();
    }

    private TrustedHeaderActorResolver resolver(boolean enabled) {
        return new TrustedHeaderActorResolver(
                new TrustedHeaderActorProperties(enabled, null, null, null)
        );
    }
}
