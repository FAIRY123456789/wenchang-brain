package cn.wenchang.brain.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import cn.wenchang.brain.controller.ArtifactController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ArtifactControllerIntegrationTest {

    private static final String ID = "123e4567-e89b-12d3-a456-426614174000";

    private Path root;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        root = Path.of("target", "test-artifacts", "main-api", java.util.UUID.randomUUID().toString())
                .toAbsolutePath().normalize();
        Path conversation = root.resolve("conversation-001");
        Files.createDirectories(conversation);
        Path file = conversation.resolve(ID + "-文昌政策简报.docx");
        Files.writeString(file, "中文 Word payload", StandardCharsets.UTF_8);
        ArtifactMetadata metadata = new ArtifactMetadata(ID, "conversation-001", "WORD", "文昌政策简报.docx",
                "2026-08-11T09:00:00Z", "policy", "policy-brief", 3,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Files.size(file),
                "conversation-001/" + file.getFileName(), "/api/artifacts/" + ID + "/download");
        new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter()
                .writeValue(conversation.resolve(ID + ".metadata.json").toFile(), metadata);
        ArtifactProperties properties = new ArtifactProperties();
        properties.setRoot(root);
        ArtifactService service = new ArtifactService(properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new ArtifactController(service)).build();
    }

    @Test
    void listsDetailsAndDownloadsChineseFilenameOverHttp() throws Exception {
        mockMvc.perform(get("/api/artifacts").queryParam("conversationId", "conversation-001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(ID))
                .andExpect(jsonPath("$[0].filename").value("文昌政策简报.docx"));
        mockMvc.perform(get("/api/artifacts/{id}", ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCount").value(3));
        var download = mockMvc.perform(get("/api/artifacts/{id}/download", ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("UTF-8''")))
                .andExpect(content().bytes("中文 Word payload".getBytes(StandardCharsets.UTF_8)))
                .andReturn();
        assertThat(download.getResponse().getHeader("Content-Disposition")).contains(".docx");
    }

    @Test
    void rejectsTraversalAndDeletesBothFileAndMetadata() throws Exception {
        mockMvc.perform(get("/api/artifacts/{id}/download", "..%2Foutside"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/api/artifacts/{id}", ID)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/artifacts/{id}", ID)).andExpect(status().isNotFound());
        try (var files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }
}
