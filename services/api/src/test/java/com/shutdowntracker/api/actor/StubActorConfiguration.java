package com.shutdowntracker.api.actor;

import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * A fixed actor for controller slice tests.
 *
 * <p>Declaring {@link Actor} on a handler is what makes an endpoint require an authenticated user,
 * so every slice test of such a controller needs a resolver. Slice tests assert routing,
 * authorisation and delegation; header parsing is covered by {@code TrustedHeaderActorResolverTests}
 * against the real resolver, so stubbing it here keeps those concerns apart.
 *
 * <p>Import it alongside {@link ActorWebMvcConfiguration}, which registers the argument resolver:
 *
 * <pre>{@code
 * @Import({ActorWebMvcConfiguration.class, StubActorConfiguration.class})
 * }</pre>
 */
@TestConfiguration
public class StubActorConfiguration {

    /** The caller every slice test acts as. A planner, so capability-allowed paths are the default. */
    public static final Actor ACTOR =
            new Actor(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "planner", "Synthetic Planner");

    @Bean
    public ActorResolver actorResolver() {
        return request -> ACTOR;
    }
}
