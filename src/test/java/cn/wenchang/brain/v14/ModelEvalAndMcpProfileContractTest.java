package cn.wenchang.brain.v14;

import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.controller.ApiController;
import cn.wenchang.brain.eval.EvalService;
import cn.wenchang.brain.model.ChatRequest;
import cn.wenchang.brain.model.RuntimeModelStatus;
import cn.wenchang.brain.rag.KnowledgeService;
import cn.wenchang.brain.runtime.RuntimeChatModelProvider;
import cn.wenchang.brain.service.ConversationMemoryService;
import cn.wenchang.brain.service.ConversationService;
import cn.wenchang.brain.service.WenchangAgentService;
import cn.wenchang.brain.tool.WebSearchTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelEvalAndMcpProfileContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void chatAndStreamRejectBeforeCreatingConversationWhenModelIsUnconfigured() {
        RuntimeChatModelProvider model = mock(RuntimeChatModelProvider.class);
        when(model.settingsStatus()).thenReturn(new RuntimeModelStatus("deepseek", "https://api.deepseek.com",
                "deepseek-chat", false, false, false, null, "UNCONFIGURED", false, null));
        ConversationService conversations = mock(ConversationService.class);
        ApiController controller = new ApiController(mock(WenchangAgentService.class), mock(KnowledgeService.class),
                mock(EvalService.class), model, new WenchangProperties(), new SyncTaskExecutor(), conversations,
                mock(ConversationMemoryService.class), mock(WebSearchTool.class));
        ChatRequest request = new ChatRequest("文昌航天有什么进展？", null, null, "aerospace", "web-search");

        assertUnconfigured(() -> controller.chat(request));
        assertUnconfigured(() -> controller.stream(request));
        org.mockito.Mockito.verifyNoInteractions(conversations);
    }

    @Test
    void agentExperienceEvalJsonCoversRequiredProfilesSkillsToolsAndMultiStepRun() throws IOException {
        JsonNode cases = MAPPER.readTree(Path.of("src/main/resources/eval/agent_experience_eval.json").toFile());
        assertThat(cases.isArray()).isTrue();
        assertThat(cases.size()).isGreaterThanOrEqualTo(7);
        assertThat(values(cases, "agentId")).contains("aerospace", "ecology", "study-tour", "policy", "wenchang");
        assertThat(values(cases, "skillId")).contains("study-tour-plan", "policy-search", "evidence-check",
                "public-service", "deep-research");

        JsonNode publicService = find(cases, "skillId", "public-service");
        assertThat(stringValues(publicService.path("expectedTools"))).contains("searchPublicServices");
        JsonNode deepResearch = find(cases, "skillId", "deep-research");
        assertThat(deepResearch.path("minSteps").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(stringValues(deepResearch.path("expectedTools"))).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void mcpProfileUsesStreamableHttpAndExtensionDeclaresThreeRealTools() throws IOException {
        String profile = Files.readString(Path.of("config/mcp-servers.yml"), StandardCharsets.UTF_8);
        String serverYaml = Files.readString(Path.of(
                "extensions/wenchang-public-resource-mcp/src/main/resources/application.yml"), StandardCharsets.UTF_8);
        String serverSource = Files.readString(Path.of(
                "extensions/wenchang-public-resource-mcp/src/main/java/cn/wenchang/mcp/PublicResourceTools.java"),
                StandardCharsets.UTF_8);

        assertThat(profile)
                .contains("on-profile: mcp", "enabled: true", "streamable-http:",
                        "url: http://127.0.0.1:8091", "endpoint: /mcp");
        assertThat(serverYaml).contains("protocol: STREAMABLE", "port: ${SERVER_PORT:8091}", "mcp-endpoint: /mcp");
        assertThat(serverSource)
                .contains("@Tool(name = \"searchPublicServices\"")
                .contains("@Tool(name = \"searchTownshipProfile\"")
                .contains("@Tool(name = \"searchStudyTourPlaces\"");
    }

    @Test
    void formalReadmeDoesNotAdvertiseDevelopmentFallbackModes() throws IOException {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
        assertThat(readme)
                .doesNotContain("`LOCAL`", "Demo Model", "Local Model", "本地演示模型", "本地回退模式")
                .contains("模型未配置", "模型设置", "DeepSeek");
    }

    private static void assertUnconfigured(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode().value()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
            assertThat(exception.getReason()).contains("模型未配置", "模型设置");
        });
    }

    private static Set<String> values(JsonNode array, String field) {
        Set<String> values = new java.util.LinkedHashSet<>();
        array.forEach(item -> {
            if (item.path(field).isTextual()) values.add(item.path(field).asText());
        });
        return values;
    }

    private static Set<String> stringValues(JsonNode array) {
        Set<String> values = new java.util.LinkedHashSet<>();
        array.forEach(item -> values.add(item.asText()));
        return values;
    }

    private static JsonNode find(JsonNode array, String field, String expected) {
        for (JsonNode item : array) if (expected.equals(item.path(field).asText())) return item;
        throw new AssertionError("Missing eval case " + field + "=" + expected);
    }
}
