package com.shutdowntracker.api.sourcefile;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/source-files")
public class SourceFileValidationController {

    private final SourceFileValidationService validationService;

    public SourceFileValidationController(SourceFileValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SourceFileValidationResponse validate(@RequestParam("file") MultipartFile file) {
        return validationService.validate(file);
    }
}
