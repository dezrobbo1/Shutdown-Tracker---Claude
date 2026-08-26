package com.shutdowntracker.api.importreview;

import java.util.UUID;

public record ImportReviewResourceRow(
        UUID id,
        String externalUid,
        String name,
        String resourceType,
        /**
         * Microsoft Project's resource Group field.
         *
         * <p>On a shutdown schedule this is where the discipline or crew lives — mechanical,
         * scaffolding, a contractor's name — which is what somebody filtering a task list actually
         * wants. A task reaches it through its assignments, so a task with several resources can
         * carry several groups.
         */
        String resourceGroup
) {
}
