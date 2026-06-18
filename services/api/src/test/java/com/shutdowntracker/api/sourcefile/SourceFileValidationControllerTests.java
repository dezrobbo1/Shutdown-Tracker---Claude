package com.shutdowntracker.api.sourcefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@WebMvcTest(SourceFileValidationController.class)
@EnableConfigurationProperties(SourceFileValidationProperties.class)
@Import({SourceFileValidationService.class, SourceFileValidationExceptionHandler.class})
@TestPropertySource(properties = "shutdown-tracker.source-file-validation.max-size-bytes=16")
class SourceFileValidationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsSyntheticMspdiXmlName() throws Exception {
        assertAccepted("synthetic-basic-wbs.mspdi.xml", ".mspdi.xml");
    }

    @Test
    void acceptsMppName() throws Exception {
        assertAccepted("example.mpp", ".mpp");
    }

    @Test
    void acceptsXmlName() throws Exception {
        assertAccepted("example.xml", ".xml");
    }

    @Test
    void acceptsUppercaseMppName() throws Exception {
        assertAccepted("EXAMPLE.MPP", ".mpp");
    }

    @Test
    void acceptsUppercaseXmlName() throws Exception {
        assertAccepted("EXAMPLE.XML", ".xml");
    }

    @Test
    void acceptsUppercaseMspdiXmlName() throws Exception {
        assertAccepted("SYNTHETIC-BASIC-WBS.MSPDI.XML", ".mspdi.xml");
    }

    @Test
    void rejectsEmptyUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "example.mpp", "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart("/api/source-files/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.rejectionReason").value("Empty files are not accepted."))
                .andExpect(jsonPath("$.message").value(containsString("no file was stored, parsed")));
    }

    @Test
    void rejectsMissingMultipartFileField() throws Exception {
        mockMvc.perform(multipart("/api/source-files/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value(nullValue()))
                .andExpect(jsonPath("$.sizeBytes").value(0))
                .andExpect(jsonPath("$.detectedExtension").value(""))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.rejectionReason").value("Missing multipart field 'file'."))
                .andExpect(jsonPath("$.message").value(containsString("no file was stored, parsed")));
    }

    @Test
    void rejectsMissingFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "", "application/octet-stream", syntheticBytes());

        mockMvc.perform(multipart("/api/source-files/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value(nullValue()))
                .andExpect(jsonPath("$.detectedExtension").value(""))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.rejectionReason").value("Missing original filename."))
                .andExpect(jsonPath("$.message").value(containsString("no file was stored, parsed")));
    }

    @Test
    void rejectsZip() throws Exception {
        assertRejectedExtension("example.zip", ".zip");
    }

    @Test
    void rejectsPdf() throws Exception {
        assertRejectedExtension("example.pdf", ".pdf");
    }

    @Test
    void rejectsDoc() throws Exception {
        assertRejectedExtension("example.doc", ".doc");
    }

    @Test
    void rejectsDocx() throws Exception {
        assertRejectedExtension("example.docx", ".docx");
    }

    @Test
    void rejectsOversizedFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "example.mpp",
                "application/octet-stream",
                "this is over sixteen bytes".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/source-files/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("example.mpp"))
                .andExpect(jsonPath("$.detectedExtension").value(".mpp"))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.rejectionReason").value("File exceeds placeholder validation limit of 16 bytes."))
                .andExpect(jsonPath("$.message").value(containsString("no file was stored, parsed")));
    }

    @Test
    void hardMultipartSizeExceptionUsesValidationJsonShape() {
        SourceFileValidationResponse response = new SourceFileValidationExceptionHandler()
                .handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1));

        assertHardMultipartResponse(
                response,
                "Multipart upload exceeds the hard request size limit before validation could run."
        );
    }

    @Test
    void multipartParseExceptionUsesValidationJsonShape() {
        SourceFileValidationResponse response = new SourceFileValidationExceptionHandler()
                .handleMultipartException(new MultipartException("Synthetic multipart parse failure"));

        assertHardMultipartResponse(response, "Multipart request could not be parsed.");
    }

    @Test
    void exceptionHandlerAcceptsSpringMultipartExceptionTypes() {
        MaxUploadSizeExceededException sizeException = new MaxUploadSizeExceededException(1);
        MultipartException parseException = new MultipartException("Synthetic multipart parse failure");

        assertThat(sizeException).isInstanceOf(MultipartException.class);
        assertThat(parseException.getMessage()).contains("Synthetic multipart parse failure");
    }

    @Test
    void exceptionHandlerIsNotScopedToControllerSelection() {
        RestControllerAdvice advice = SourceFileValidationExceptionHandler.class.getAnnotation(RestControllerAdvice.class);

        // Hard multipart failures can happen before Spring selects SourceFileValidationController.
        assertThat(advice.assignableTypes()).isEmpty();
    }

    @Test
    void rejectsImages() throws Exception {
        assertRejectedExtension("screenshot.png", ".png");
    }

    @Test
    void rejectsXer() throws Exception {
        assertRejectedExtension("example.xer", ".xer");
    }

    private void assertAccepted(String filename, String expectedExtension) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/octet-stream", syntheticBytes());

        mockMvc.perform(multipart("/api/source-files/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value(filename))
                .andExpect(jsonPath("$.sizeBytes").value(9))
                .andExpect(jsonPath("$.detectedExtension").value(expectedExtension))
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.rejectionReason").value(nullValue()))
                .andExpect(jsonPath("$.message").value(containsString("no file was stored, parsed")));
    }

    private void assertRejectedExtension(String filename, String expectedExtension) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/octet-stream", syntheticBytes());

        mockMvc.perform(multipart("/api/source-files/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value(filename))
                .andExpect(jsonPath("$.detectedExtension").value(expectedExtension))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.rejectionReason").value("Unsupported source file extension."))
                .andExpect(jsonPath("$.message").value(containsString("no file was stored, parsed")));
    }

    private byte[] syntheticBytes() {
        return "synthetic".getBytes(StandardCharsets.UTF_8);
    }

    private void assertHardMultipartResponse(SourceFileValidationResponse response, String rejectionReason) {
        assertThat(response.originalFilename()).isNull();
        assertThat(response.sizeBytes()).isZero();
        assertThat(response.detectedExtension()).isEmpty();
        assertThat(response.accepted()).isFalse();
        assertThat(response.rejectionReason()).isEqualTo(rejectionReason);
        assertThat(response.message()).contains("no file was stored, parsed");
    }
}
