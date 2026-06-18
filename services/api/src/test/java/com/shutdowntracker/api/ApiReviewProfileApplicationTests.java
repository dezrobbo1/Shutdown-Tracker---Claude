package com.shutdowntracker.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("review")
class ApiReviewProfileApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reviewProfileLoadsWithoutPostgreSqlOrFlyway() {
        assertThat(applicationContext.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(Flyway.class)).isEmpty();
    }

    @Test
    void reviewProfileExposesHealthAndVersion() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("shutdown-tracker-api"))
                .andExpect(jsonPath("$.status").value("placeholder"));
    }

    @Test
    void reviewProfileValidatesSyntheticSourceFileOnly() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "synthetic-basic-wbs.mspdi.xml",
                "application/xml",
                "synthetic".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/source-files/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("synthetic-basic-wbs.mspdi.xml"))
                .andExpect(jsonPath("$.sizeBytes").value(9))
                .andExpect(jsonPath("$.detectedExtension").value(".mspdi.xml"))
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.rejectionReason").value(nullValue()))
                .andExpect(jsonPath("$.message").value(containsString("no file was stored, parsed")));
    }
}
