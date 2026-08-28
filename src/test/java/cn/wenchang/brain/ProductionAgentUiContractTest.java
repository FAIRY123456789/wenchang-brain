package cn.wenchang.brain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionAgentUiContractTest {

    private static final Path STATIC_ROOT = Path.of("src", "main", "resources", "static");

    @Test
    void markdownUsesOneLocalSafeRendererForStreamingAndHistory() throws IOException {
        String html = read("index.html");
        String app = read("app.js");
        String css = read("styles.css");

        assertThat(Files.size(STATIC_ROOT.resolve("vendor/marked.min.js"))).isGreaterThan(30_000);
        assertThat(Files.size(STATIC_ROOT.resolve("vendor/purify.min.js"))).isGreaterThan(15_000);
        assertThat(html)
                .contains("vendor/marked.min.js", "vendor/purify.min.js", "app.js")
                .containsSubsequence("vendor/marked.min.js", "vendor/purify.min.js", "app.js");
        assertThat(app)
                .contains("function renderMarkdown(markdown, target)")
                .contains("window.marked.parse", "window.DOMPurify.sanitize")
                .contains("rawMarkdownBuffer", "appendMarkdownChunk(assistant", "finalizeAssistantMarkdown")
                .contains("renderMarkdown(content || '', body)")
                .contains("link.target = '_blank'", "noopener noreferrer")
                .doesNotContain("assistant.content.textContent += data.text");
        assertThat(css).contains(".message.assistant .message-content h1", "table", "blockquote", "pre code");
    }

    @Test
    void exposesAgentDetailContextArtifactsApprovalAndDiagnostics() throws IOException {
        String html = read("index.html");
        String app = read("app.js");
        String css = read("styles.css");

        assertThat(html).contains(
                "id=\"agentDetailDialog\"", "id=\"agentDetailCapabilities\"",
                "id=\"agentDetailWorkflow\"", "id=\"agentDetailApproval\"",
                "id=\"runAgentDiagnosticsButton\"", "data-diagnostic=\"model\"",
                "data-diagnostic=\"dataExport\"", "id=\"approvalDialog\"");
        assertThat(app).contains(
                "function agentContextCard(agent)", "function openAgentDetail(id, returnFocus",
                "function addArtifactCards(element, artifacts)", "event === 'artifact_created'",
                "event === 'approval_required'", "function runAgentDiagnostics()",
                "const artifacts = parseJson(message.artifactsJson || message.artifactJson || message.artifacts, [])")
                .contains("String(value.status || value.health || '').toUpperCase()")
                .contains("function createMessageActions(content, metadata = {}, messageElement)", "function copyMessageText(content, button)",
                        "function fallbackCopyText(value)", "function editUserMessage(content, metadata, messageElement)",
                        "function activateMessageRevision(messageId)", "function createRevisionNavigator(revisions)")
                .contains("if (role === 'user') element.append(createMessageActions(content || '', metadata || {}, element))");
        assertThat(app).contains("artifact-name", "artifact-meta", "artifact-download", "t('artifact.download')")
                .doesNotContain("open.textContent = '打开'")
                .contains("appUrl(`/api/artifacts/${encodeURIComponent")
                .contains("function safeArtifactUrl(value)")
                .contains("artifactCount ? ` · ${artifactCount} 个文件`")
                .doesNotContain("refreshConversationArtifacts", "/api/artifacts?conversationId=");
        assertThat(app).contains("/api/agent/approvals/${encodeURIComponent(id)}");
        assertThat(css).contains(
                ".agent-context-card", ".agent-detail-panel", ".skill-detail-panel", ".artifact-card",
                ".artifact-card .artifact-download", ".approval-panel", ".diagnostics-grid",
                ".message-actions", ".message-action:focus-visible", ".message-revisions",
                ".message-inline-editor", ".message-inline-submit");
        assertThat(app).contains("!event.shiftKey", "!event.isComposing", "event.keyCode !== 229",
                "messageElement.classList.add('editing')");
        assertThat(css).contains(".message.user.editing", "background: linear-gradient(145deg");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(STATIC_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
