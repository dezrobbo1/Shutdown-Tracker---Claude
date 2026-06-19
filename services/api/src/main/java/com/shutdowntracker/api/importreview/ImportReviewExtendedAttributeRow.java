package com.shutdowntracker.api.importreview;

import com.shutdowntracker.api.importedproject.ImportedExtendedAttributeEntityType;
import java.util.UUID;

public record ImportReviewExtendedAttributeRow(
        UUID id,
        ImportedExtendedAttributeEntityType entityType,
        String entityExternalUid,
        String fieldId,
        String fieldName,
        String alias,
        String value
) {
}
