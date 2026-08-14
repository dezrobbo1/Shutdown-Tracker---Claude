package com.shutdowntracker.api.audit;

public final class AuditEventTypes {

    public static final String IMPORT_SNAPSHOT_ACCEPTED = "import_snapshot_accepted";
    public static final String IMPORT_SNAPSHOT_REJECTED = "import_snapshot_rejected";
    public static final String SOURCE_FILE_UPLOADED = "source_file_uploaded";
    public static final String REIMPORT_LINEAGE_LINK_CREATED = "reimport_lineage_link_created";
    public static final String REIMPORT_LINEAGE_LINK_ACCEPTED = "reimport_lineage_link_accepted";
    public static final String REIMPORT_LINEAGE_LINK_REJECTED = "reimport_lineage_link_rejected";
    public static final String APPROVAL_RECORDED = "approval_recorded";
    public static final String APPROVAL_APPROVED_FOR_EXPORT = "approval_approved_for_export";
    public static final String APPROVAL_REJECTED = "approval_rejected";
    public static final String APPROVAL_CORRECTION_REQUESTED = "approval_correction_requested";
    public static final String IMPORT_BATCH_PARSE_FAILED = "import_batch_parse_failed";
    public static final String IMPORT_SNAPSHOT_STORED = "import_snapshot_stored";
    public static final String EXPORT_FILE_GENERATION_FAILED = "export_file_generation_failed";
    public static final String EXPORT_PREVIEW_CREATED = "export_preview_created";
    public static final String EXPORT_BATCH_APPROVED = "export_batch_approved";
    public static final String EXPORT_BATCH_REJECTED = "export_batch_rejected";
    public static final String EXPORT_FILE_GENERATED = "export_file_generated";
    public static final String EXPORT_FILE_OPENED_IN_MICROSOFT_PROJECT =
            "export_file_opened_in_microsoft_project";
    public static final String EXPORT_FILE_VERIFIED = "export_file_verified";

    private AuditEventTypes() {
    }
}
