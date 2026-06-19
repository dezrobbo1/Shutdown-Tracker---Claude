package com.shutdowntracker.api.sourcefile.metadata;

import java.util.Arrays;
import java.util.Locale;

public enum SourceFileKind {
    MPP("mpp"),
    MSPDI_XML("mspdi_xml"),
    XML("xml"),
    OTHER("other");

    private final String databaseValue;

    SourceFileKind(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static SourceFileKind fromOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return OTHER;
        }

        String lowerFilename = originalFilename.toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".mspdi.xml")) {
            return MSPDI_XML;
        }
        if (lowerFilename.endsWith(".mpp")) {
            return MPP;
        }
        if (lowerFilename.endsWith(".xml")) {
            return XML;
        }
        return OTHER;
    }

    public static SourceFileKind fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(kind -> kind.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported source file kind: " + databaseValue));
    }
}
