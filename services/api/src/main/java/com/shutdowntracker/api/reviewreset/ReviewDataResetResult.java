package com.shutdowntracker.api.reviewreset;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What the reset actually did, table by table.
 *
 * <p>Per-table counts rather than a bare acknowledgement: "done" gives a person no way to tell a
 * reset that cleared 1,155 imported tasks from one that silently matched nothing.
 */
public record ReviewDataResetResult(
        UUID projectId,
        String projectName,
        OffsetDateTime resetAt,
        List<TableReset> tables,
        List<BlobReset> blobs,
        List<String> keptTables,
        List<String> warnings
) {

    public record TableReset(String name, long rowsDeleted) {
    }

    /**
     * @param error null when the directory was cleared; otherwise why it was not, so a caller can
     *              see that the database was reset and the files were not.
     */
    public record BlobReset(String root, long filesDeleted, long bytesFreed, String error) {
    }
}
