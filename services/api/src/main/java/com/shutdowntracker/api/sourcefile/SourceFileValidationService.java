package com.shutdowntracker.api.sourcefile;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SourceFileValidationService {

    private static final Set<String> ACCEPTED_EXTENSIONS = Set.of(".mpp", ".xml", ".mspdi.xml");

    private final SourceFileValidationProperties properties;

    public SourceFileValidationService(SourceFileValidationProperties properties) {
        this.properties = properties;
    }

    public SourceFileValidationResponse validate(MultipartFile file) {
        String originalFilename = normalizeFilename(file.getOriginalFilename());
        long sizeBytes = file.getSize();
        String detectedExtension = detectExtension(originalFilename);

        String rejectionReason = rejectionReason(originalFilename, sizeBytes, detectedExtension);
        boolean accepted = rejectionReason == null;

        return new SourceFileValidationResponse(
                originalFilename,
                sizeBytes,
                detectedExtension,
                accepted,
                rejectionReason,
                SourceFileValidationMessages.NOT_STORED_OR_PARSED
        );
    }

    private String rejectionReason(String originalFilename, long sizeBytes, String detectedExtension) {
        if (originalFilename == null) {
            return "Missing original filename.";
        }
        if (sizeBytes <= 0) {
            return "Empty files are not accepted.";
        }
        if (sizeBytes > properties.maxSizeBytes()) {
            return "File exceeds placeholder validation limit of " + properties.maxSizeBytes() + " bytes.";
        }
        if (!ACCEPTED_EXTENSIONS.contains(detectedExtension)) {
            return "Unsupported source file extension.";
        }
        return null;
    }

    private String normalizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        return originalFilename.trim();
    }

    private String detectExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }

        String lowerFilename = originalFilename.toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".mspdi.xml")) {
            return ".mspdi.xml";
        }

        int dotIndex = lowerFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == lowerFilename.length() - 1) {
            return "";
        }
        return lowerFilename.substring(dotIndex);
    }
}
