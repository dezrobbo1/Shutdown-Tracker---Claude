package com.shutdowntracker.api.sourcefile;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
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
    private final ProjectAuthorizationService authorization;

    public SourceFileUploadController(SourceFileUploadService uploadService, ProjectAuthorizationService authorization) {
        this.uploadService = uploadService;
        this.authorization = authorization;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SourceFileUploadResponse upload(
            @PathVariable UUID projectId,
            Actor actor,
            @RequestParam("file") MultipartFile file
    ) {
        authorization.requireCapability(projectId, actor, Capability.UPLOAD_SOURCE_FILE);
        return uploadService.upload(projectId, actor, file);
    }
}
