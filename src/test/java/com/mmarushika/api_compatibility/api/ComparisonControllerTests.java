package com.mmarushika.api_compatibility.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ComparisonControllerTests {

    private static final String OLD_DOCUMENT = """
            openapi: 3.0.0
            info:
              title: Test
              version: 1.0.0
            paths:
              /users:
                get:
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema:
                            type: integer
            """;

    private static final String NEW_DOCUMENT = """
            openapi: 3.0.0
            info:
              title: Test
              version: 1.0.0
            paths:
              /users:
                get:
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema:
                            type: string
            """;

    @Autowired
    private ComparisonController comparisonController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(comparisonController).build();
    }

    @Test
    void returnsDetectedChangesForTheComparedDocuments() throws Exception {
        String request = objectMapper.writeValueAsString(
                new CompareRequest(OLD_DOCUMENT, NEW_DOCUMENT));

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("SCHEMA_TYPE_CHANGED"))
                .andExpect(jsonPath("$[0].impact").value("BREAKING"))
                .andExpect(jsonPath("$[0].location.path").value("/users"))
                .andExpect(jsonPath("$[0].location.method").value("GET"))
                .andExpect(jsonPath("$[0].location.statusCode").value("200"))
                .andExpect(jsonPath("$[0].location.mediaType").value("application/json"));
    }

    @Test
    void returnsAnEmptyArrayWhenTheDocumentsAreIdentical() throws Exception {
        String request = objectMapper.writeValueAsString(
                new CompareRequest(OLD_DOCUMENT, OLD_DOCUMENT));

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsAnEmptyDocument() throws Exception {
        String request = objectMapper.writeValueAsString(
                new CompareRequest("", OLD_DOCUMENT));

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsAnUnreadableDocument() throws Exception {
        String request = objectMapper.writeValueAsString(
                new CompareRequest("{invalid", OLD_DOCUMENT));

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void returnsABreakingReportWithASummary() throws Exception {
        String request = objectMapper.writeValueAsString(
                new CompareRequest(OLD_DOCUMENT, NEW_DOCUMENT));

        mockMvc.perform(post("/api/compare/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breaking").value(true))
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.summary.breaking").value(1))
                .andExpect(jsonPath("$.summary.nonBreaking").value(0))
                .andExpect(jsonPath("$.summary.unknown").value(0))
                .andExpect(jsonPath("$.changes[0].type").value("SCHEMA_TYPE_CHANGED"));
    }

    @Test
    void returnsACompatibleReportWithoutBreakingChanges() throws Exception {
        String request = objectMapper.writeValueAsString(
                new CompareRequest(OLD_DOCUMENT, OLD_DOCUMENT));

        mockMvc.perform(post("/api/compare/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breaking").value(false))
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.changes").isEmpty());
    }

    @Test
    void failsTheRequestWhenBreakingChangesAreRequestedToFail() throws Exception {
        String request = objectMapper.writeValueAsString(
                new CompareRequest(OLD_DOCUMENT, NEW_DOCUMENT));

        mockMvc.perform(post("/api/compare/report")
                        .queryParam("failOnBreaking", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.breaking").value(true))
                .andExpect(jsonPath("$.changes[0].type").value("SCHEMA_TYPE_CHANGED"));
    }

    @Test
    void comparesUploadedDocumentFiles() throws Exception {
        mockMvc.perform(multipart("/api/compare/files")
                        .file(document("old", OLD_DOCUMENT))
                        .file(document("new", NEW_DOCUMENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breaking").value(true))
                .andExpect(jsonPath("$.summary.breaking").value(1))
                .andExpect(jsonPath("$.changes[0].type").value("SCHEMA_TYPE_CHANGED"));
    }

    @Test
    void failsAnUploadedComparisonWhenBreakingChangesAreRequestedToFail() throws Exception {
        mockMvc.perform(multipart("/api/compare/files")
                        .file(document("old", OLD_DOCUMENT))
                        .file(document("new", NEW_DOCUMENT))
                        .queryParam("failOnBreaking", "true"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.breaking").value(true));
    }

    @Test
    void rejectsAnUnreadableUploadedDocument() throws Exception {
        mockMvc.perform(multipart("/api/compare/files")
                        .file(document("old", "{invalid"))
                        .file(document("new", NEW_DOCUMENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private static MockMultipartFile document(String partName, String contents) {
        return new MockMultipartFile(
                partName,
                partName + ".yaml",
                "application/yaml",
                contents.getBytes(StandardCharsets.UTF_8));
    }
}
