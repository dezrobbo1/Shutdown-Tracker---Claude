package com.shutdowntracker.api.audit;

public final class AuditEventTypes {

    public static final String IMPORT_SNAPSHOT_ACCEPTED = "import_snapshot_accepted";
    public static final String IMPORT_SNAPSHOT_REJECTED = "import_snapshot_rejected";
    public static final String REIMPORT_LINEAGE_LINK_CREATED = "reimport_lineage_link_created";
    public static final String REIMPORT_LINEAGE_LINK_ACCEPTED = "reimport_lineage_link_accepted";
    public static final String REIMPORT_LINEAGE_LINK_REJECTED = "reimport_lineage_link_rejected";
    public static final String EXPORT_PREVIEW_CREATED = "export_preview_created";

    private AuditEventTypes() {
    }
}
