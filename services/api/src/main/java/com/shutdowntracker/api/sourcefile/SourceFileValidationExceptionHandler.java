package com.shutdowntracker.api.sourcefile;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = SourceFileValidationController.class)
public class SourceFileValidationExceptionHandler {

    @ExceptionHandler(MissingServletRequestPartException.class)
    public SourceFileValidationResponse handleMissingFile(MissingServletRequestPartException exception) {
        String partName = exception.getRequestPartName();
        String reason = "Missing multipart field '" + partName + "'.";
        return rejected(reason);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public SourceFileValidationResponse handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return rejected("Multipart upload exceeds the hard request size limit before validation could run.");
    }

    @ExceptionHandler(MultipartException.class)
    public SourceFileValidationResponse handleMultipartException(MultipartException exception) {
        return rejected("Multipart request could not be parsed.");
    }

    private SourceFileValidationResponse rejected(String rejectionReason) {
        return new SourceFileValidationResponse(
                null,
                0,
                "",
                false,
                rejectionReason,
                SourceFileValidationMessages.NOT_STORED_OR_PARSED
        );
    }
}
