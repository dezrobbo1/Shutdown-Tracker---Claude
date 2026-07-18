package com.shutdowntracker.api.exportpreview;

public final class ExportIntegrityPolicy {

    public static final int CURRENT_VERSION = 1;

    private ExportIntegrityPolicy() {
    }

    public static boolean isCurrent(Integer version) {
        return version != null && version == CURRENT_VERSION;
    }
}
