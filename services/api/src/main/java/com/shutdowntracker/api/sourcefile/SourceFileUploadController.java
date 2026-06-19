package com.shutdowntracker.api.sourcefile;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/source-files")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class SourceFileUploadController {

    private final SourceFileUploadService uploadService;

    public SourceFileUploadController(SourceFileUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SourceFileUploadResponse upload(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file
    ) {
        return uploadService.upload(projectId, file);
    }
}
