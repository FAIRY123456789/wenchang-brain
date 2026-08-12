package cn.wenchang.brain.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ArtifactPipelineContractTest {
    private Path root;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        root = Path.of("target", "artifact-pipeline", java.util.UUID.randomUUID().toString()).toAbsolutePath();
        Files.createDirectories(root.resolve("conversation-01"));
        ArtifactProperties properties = new ArtifactProperties();
        properties.setRoot(root);
        mvc = MockMvcBuilders.standaloneSetup(
                new cn.wenchang.brain.controller.ArtifactController(new ArtifactService(properties))).build();
    }

    @Test
    void wordDescriptorAndDownloadAreComplete() throws Exception {
        create("word-01", "WORD", "文昌市高中名单报告.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "word-bytes", 6);
        mvc.perform(get("/api/artifacts").param("conversationId", "conversation-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("word-01"))
                .andExpect(jsonPath("$[0].displayName").value("文昌市高中名单报告"))
                .andExpect(jsonPath("$[0].mimeType").value(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(jsonPath("$[0].sizeBytes").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[0].downloadUrl").isNotEmpty());
        mvc.perform(get("/api/artifacts/word-01/download")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("UTF-8''")))
                .andExpect(header().longValue("Content-Length", 10));
    }

    @Test
    void xlsxAndCsvUseSameDescriptorContract() throws Exception {
        create("xlsx-01", "XLSX", "文昌研学地点.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx", 0);
        create("csv-01", "CSV", "文昌研学地点.csv", "text/csv", "csv", 0);
        mvc.perform(get("/api/artifacts").param("conversationId", "conversation-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[*].mimeType", org.hamcrest.Matchers.hasItems("text/csv",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
    }

    @Test
    void metadataWithoutFileReturns404() throws Exception {
        writeManifest("missing-file", "CSV", "missing.csv", "text/csv", 12, 0);
        mvc.perform(get("/api/artifacts/missing-file/download")).andExpect(status().isNotFound());
        mvc.perform(get("/api/artifacts/not-found/download")).andExpect(status().isNotFound());
    }

    private void create(String id, String type, String name, String mime, String value, int sourceCount) throws Exception {
        Files.writeString(root.resolve("conversation-01").resolve(id + "-" + name), value, StandardCharsets.UTF_8);
        writeManifest(id, type, name, mime, value.getBytes(StandardCharsets.UTF_8).length, sourceCount);
    }

    private void writeManifest(String id, String type, String name, String mime, long size, int sourceCount) throws Exception {
        ArtifactMetadata metadata = new ArtifactMetadata(id, "conversation-01", type, name,
                "2026-08-12T00:00:00Z", "wenchang", "artifact-test", sourceCount, mime, size,
                "conversation-01/" + id + "-" + name, "/api/artifacts/" + id + "/download");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                root.resolve("conversation-01").resolve(id + ".metadata.json").toFile(), metadata);
    }
}
