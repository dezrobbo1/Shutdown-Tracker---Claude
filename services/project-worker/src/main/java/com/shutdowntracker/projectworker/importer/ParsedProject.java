package com.shutdowntracker.projectworker.importer;

import java.time.OffsetDateTime;
import java.util.List;
import com.shutdowntracker.projectimport.contract.ParsedAssignment;
import com.shutdowntracker.projectimport.contract.ParsedExtendedAttribute;
import com.shutdowntracker.projectimport.contract.ParsedResource;
import com.shutdowntracker.projectimport.contract.ParsedTask;

/**
 * Everything one read of a Microsoft Project file produced: the counts already reported
 * by the summary endpoint, plus the entities themselves.
 */
public record ParsedProject(
        ProjectImportSummary summary,
        String externalProjectUid,
        OffsetDateTime projectStatusDate,
        List<ParsedTask> tasks,
        List<ParsedResource> resources,
        List<ParsedAssignment> assignments,
        List<ParsedExtendedAttribute> extendedAttributes
) {
    public ParsedProject {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        resources = resources == null ? List.of() : List.copyOf(resources);
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
        extendedAttributes = extendedAttributes == null ? List.of() : List.copyOf(extendedAttributes);
    }
}
