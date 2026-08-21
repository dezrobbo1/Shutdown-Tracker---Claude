package com.shutdowntracker.api.reviewdemo;

import com.shutdowntracker.api.identity.ProjectRole;
import java.util.UUID;

/**
 * A seeded review identity, as the picker needs to know it.
 *
 * <p>Carries {@code projectId} because a seeded identity is only powerful on the project it holds a
 * membership on. If an application is pointed at a different project, every write by this user is
 * refused with "no active membership on this project" — a failure that reads like a defect. Handing
 * the project back with the person lets the caller say which it is instead.
 *
 * <p>Deliberately absent: email, external subject, status. The picker needs to name a person and
 * act as them; it has no use for how they would authenticate if authentication existed.
 */
public record ReviewDemoIdentity(
        UUID id,
        String displayName,
        ProjectRole role,
        UUID projectId
) {
}
