package cn.wenchang.brain.agent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WenchangToolRegistryNameMatchingTest {

    @Test
    void exclusionsMatchNativeAndPrefixedMcpToolNames() {
        Set<String> excluded = Set.of("webSearch", "createWenchangWordReport");

        assertThat(WenchangToolRegistry.matchesToolName(excluded, "webSearch")).isTrue();
        assertThat(WenchangToolRegistry.matchesToolName(excluded,
                "wenchang_task_mcp_createWenchangWordReport")).isTrue();
        assertThat(WenchangToolRegistry.matchesToolName(excluded, "knowledgeEvidence")).isFalse();
    }
}