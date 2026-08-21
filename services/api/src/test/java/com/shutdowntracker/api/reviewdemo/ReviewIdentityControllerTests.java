package com.shutdowntracker.api.reviewdemo;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shutdowntracker.api.identity.ProjectRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewIdentityController.class)
@EnableConfigurationProperties(ReviewDemoIdentityProperties.class)
@TestPropertySource(properties = {
        "shutdown-tracker.review-demo-identities.enabled=true",
        "shutdown-tracker.persistence.enabled=true"
})
class ReviewIdentityControllerTests {

    private static final UUID PROJECT_ID = UUID.fromString("2f97e590-343f-4de2-bd30-7c19a5db7a64");
    private static final UUID FIELD_USER_ID = UUID.fromString("6140706a-c178-41c8-8a8c-45f61640a6d7");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewDemoIdentityRepository identityRepository;

    @Test
    void reportsTheRoleInTheFormTheClientAndTheSchemaUse() throws Exception {
        when(identityRepository.findSeeded("synthetic-review-identities")).thenReturn(List.of(
                new ReviewDemoIdentity(FIELD_USER_ID, "Review Field User", ProjectRole.FIELD_USER, PROJECT_ID)));

        // The enum constant is FIELD_USER; the database value, the permission matrix and the
        // TypeScript ProjectRole union all say field_user. Emitting the constant name shipped a
        // role the client could not recognise: labels rendered blank and a stored identity was
        // discarded as invalid. This is the assertion that catches it.
        mockMvc.perform(get("/api/review-identities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("field_user"));
    }

    @Test
    void answersWithoutAnActorHeader() throws Exception {
        when(identityRepository.findSeeded("synthetic-review-identities")).thenReturn(List.of());

        // Requiring an actor would be a chicken and egg: this is the question asked before an
        // identity has been chosen.
        mockMvc.perform(get("/api/review-identities"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void saysOnlyWhoSomebodyIsAndWhereTheyCanAct() throws Exception {
        when(identityRepository.findSeeded("synthetic-review-identities")).thenReturn(List.of(
                new ReviewDemoIdentity(FIELD_USER_ID, "Review Field User", ProjectRole.FIELD_USER, PROJECT_ID)));

        mockMvc.perform(get("/api/review-identities"))
                .andExpect(jsonPath("$[0].id").value(FIELD_USER_ID.toString()))
                .andExpect(jsonPath("$[0].displayName").value("Review Field User"))
                .andExpect(jsonPath("$[0].projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].externalSubject").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist());
    }
}
