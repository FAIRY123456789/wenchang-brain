package cn.wenchang.brain.rag;

import cn.wenchang.brain.config.WenchangProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIngestionSupervisorTest {

    private final Path temporaryDirectory = Path.of("target", "ingestion-supervisor-test");

    @BeforeEach
    void prepareWorkspaceLocalFixture() throws Exception {
        Files.createDirectories(temporaryDirectory);
    }

    @Test
    void recursivelyIngestsActiveMarkdownAndSkipsCanonicalDuplicatesAndLegacy() throws Exception {
        Path category = temporaryDirectory.resolve("03-spaceflight");
        Files.createDirectories(category);
        Files.createDirectories(temporaryDirectory.resolve("_legacy"));
        Files.writeString(category.resolve("launch-site.md"), document("WC-CNSA-001", "active"), StandardCharsets.UTF_8);
        Files.writeString(category.resolve("duplicate.md"), document("WC-CNSA-002", "active"), StandardCharsets.UTF_8);
        Files.writeString(category.resolve("inactive.md"), document("WC-CNSA-003", "inactive"), StandardCharsets.UTF_8);
        Files.writeString(temporaryDirectory.resolve("_legacy/old.md"), "# old\n\nlegacy content that is excluded.", StandardCharsets.UTF_8);

        WenchangProperties properties = new WenchangProperties();
        properties.setKnowledgeDir(temporaryDirectory.toString());
        var prepared = new KnowledgeIngestionSupervisor(properties).prepare();

        assertThat(prepared.files()).isEqualTo(1);
        assertThat(prepared.sources()).isEqualTo(1);
        assertThat(prepared.duplicatesSkipped()).isEqualTo(1);
        assertThat(prepared.categories()).containsEntry("03-spaceflight", 1);
        assertThat(prepared.sourceLevels()).containsEntry("P0", 1);
        assertThat(prepared.chunks()).isNotEmpty();
        assertThat(prepared.chunks().get(0).getMetadata())
                .containsEntry("source_organization", "国家航天局")
                .containsEntry("source_url", "https://www.cnsa.gov.cn/example")
                .containsEntry("section", "测试资料 > 事实");
    }

    private String document(String sourceId, String indexStatus) {
        return """
                ---
                title: 测试资料
                source_id: %s
                source_organization: 国家航天局
                source_url: https://www.cnsa.gov.cn/example
                source_level: P0
                published_at: 2025-01-01
                retrieved_at: 2026-08-11
                updated_at: 2025-01-01
                tags: [文昌, 航天]
                index_status: %s
                temporal_type: stable
                category: 03-spaceflight
                ---
                # 测试资料

                ## 事实

                文昌航天发射场是我国重要的滨海航天发射设施，本段提供足够长度用于验证递归摄取、元数据传播和章节切分行为。
                """.formatted(sourceId, indexStatus);
    }
}
