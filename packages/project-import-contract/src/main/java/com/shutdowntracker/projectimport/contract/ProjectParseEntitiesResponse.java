package com.shutdowntracker.projectimport.contract;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * The full parsed contents of a Microsoft Project file.
 *
 * <p>This is the contract {@code ProjectParseSummaryResponse} could not satisfy. That
 * response carried only counts, so the parsed schedule was discarded after every import
 * and nothing downstream — execution state, review, problems, evidence, export of actuals
 * — had any task to attach to.
 *
 * <p>Tasks arrive in the order the parser produced them, which places a parent ahead of
 * its children. The API relies on that ordering to resolve {@code parentExternalUid} to a
 * stored row as it inserts.
 */
public record ProjectParseEntitiesResponse(
        ProjectParseSummaryResponse summary,
        String externalProjectUid,
        OffsetDateTime projectStatusDate,
        List<ParsedTask> tasks,
        List<ParsedResource> resources,
        List<ParsedAssignment> assignments,
        List<ParsedExtendedAttribute> extendedAttributes
) {
    public ProjectParseEntitiesResponse {
        Objects.requireNonNull(summary, "summary is required.");
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        resources = resources == null ? List.of() : List.copyOf(resources);
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
        extendedAttributes = extendedAttributes == null ? List.of() : List.copyOf(extendedAttributes);
    }
}
