package com.shutdowntracker.api.sourcefile;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.importbatch.ImportBatchStatus;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileKind;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRecord;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.shutdowntracker.api.actor.ActorWebMvcConfiguration;
import com.shutdowntracker.api.actor.StubActorConfiguration;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(SourceFileUploadController.class)
@Import({
    SourceFileValidationExceptionHandler.class,
    ActorWebMvcConfiguration.class,
    StubActorConfiguration.class
})
@TestPropertySource(properties = "shutdown-tracker.persistence.enabled=true")
class SourceFileUploadControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SourceFileUploadService uploadService;

    /**
     * Authorisation itself is covered by ProjectAuthorizationServiceTests against the real
     * membership store; here it is mocked so a slice test can assert the controller consults it.
     */
    @MockBean
    private ProjectAuthorizationService authorization;

    @Test
    void uploadsProjectSourceFileThroughOrchestrationEndpoint() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID sourceFileId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "synthetic-basic-wbs.mspdi.xml",
                "application/xml",
                "synthetic".getBytes(StandardCharsets.UTF_8)
        );
        SourceFileUploadResponse response = new SourceFileUploadResponse(
                "synthetic-basic-wbs.mspdi.xml",
                9,
                ".mspdi.xml",
                true,
                null,
                new SourceFileMetadataRecord(
                        sourceFileId,
                        projectId,
                        "synthetic-basic-wbs.mspdi.xml",
                        SourceFileKind.MSPDI_XML,
                        "file:///synthetic-basic-wbs.mspdi.xml",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        9
                ),
                new ImportBatchRecord(
                        importBatchId,
                        projectId,
                        sourceFileId,
                        ImportBatchStatus.PENDING,
                        null,
                        null,
                        0,
                        0
                ),
                "Source file stored and pending import batch created. No file was parsed, forwarded to the worker, imported, or written back to Microsoft Project."
        );
        when(uploadService.upload(eq(projectId), eq(StubActorConfiguration.ACTOR), any(MultipartFile.class))).thenReturn(response);

        mockMvc.perform(multipart("/api/projects/{projectId}/source-files", projectId).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.sourceFile.id").value(sourceFileId.toString()))
                .andExpect(jsonPath("$.sourceFile.fileKind").value("MSPDI_XML"))
                .andExpect(jsonPath("$.importBatch.id").value(importBatchId.toString()))
                .andExpect(jsonPath("$.importBatch.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value(containsString("No file was parsed")));

        verify(uploadService).upload(eq(projectId), eq(StubActorConfiguration.ACTOR), any(MultipartFile.class));
    }

    @Test
    void missingMultipartFieldUsesValidationJsonShape() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(multipart("/api/projects/{projectId}/source-files", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value(nullValue()))
                .andExpect(jsonPath("$.sizeBytes").value(0))
                .andExpect(jsonPath("$.detectedExtension").value(""))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.rejectionReason").value("Missing multipart field 'file'."))
                .andExpect(jsonPath("$.message").value(containsString("no file was stored, parsed")));

        verifyNoInteractions(uploadService);
    }

    @Test
    void refusesAnUploadWhenTheActorLacksTheCapability() throws Exception {
        UUID projectId = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Role supervisor may not upload schedules."))
                .when(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.UPLOAD_SOURCE_FILE);

        mockMvc.perform(multipart("/api/projects/{projectId}/source-files", projectId)
                        .file(new MockMultipartFile("file", "schedule.xml", "text/xml", "<Project/>".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(uploadService);
    }

}
