package com.shutdowntracker.api.sourcefile.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceFileKindTests {

    @Test
    void detectsSupportedSourceFileKindsFromOriginalFilename() {
        assertThat(SourceFileKind.fromOriginalFilename("example.mpp")).isEqualTo(SourceFileKind.MPP);
        assertThat(SourceFileKind.fromOriginalFilename("example.xml")).isEqualTo(SourceFileKind.XML);
        assertThat(SourceFileKind.fromOriginalFilename("synthetic-basic-wbs.mspdi.xml"))
                .isEqualTo(SourceFileKind.MSPDI_XML);
    }

    @Test
    void detectsUppercaseExtensions() {
        assertThat(SourceFileKind.fromOriginalFilename("EXAMPLE.MPP")).isEqualTo(SourceFileKind.MPP);
        assertThat(SourceFileKind.fromOriginalFilename("EXAMPLE.XML")).isEqualTo(SourceFileKind.XML);
        assertThat(SourceFileKind.fromOriginalFilename("SYNTHETIC-BASIC-WBS.MSPDI.XML"))
                .isEqualTo(SourceFileKind.MSPDI_XML);
    }

    @Test
    void fallsBackToOtherForUnknownOrMissingFilenames() {
        assertThat(SourceFileKind.fromOriginalFilename("example.zip")).isEqualTo(SourceFileKind.OTHER);
        assertThat(SourceFileKind.fromOriginalFilename("")).isEqualTo(SourceFileKind.OTHER);
        assertThat(SourceFileKind.fromOriginalFilename(null)).isEqualTo(SourceFileKind.OTHER);
    }
}
