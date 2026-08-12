package cn.wenchang.brain.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyLatestQueryTest {

    @Test
    void naturalLatestPolicyQuestionReturnsNewestStructuredPolicies() {
        PolicySearchTool tool = new PolicySearchTool("data/wenchang-policies.json");
        List<PolicySearchTool.PolicyItem> result = tool.search("我想知道最新的文昌政策", "", "", 10);

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(item -> {
            assertThat(item.organization()).isNotBlank();
            assertThat(item.publishedAt()).isNotBlank();
            assertThat(item.sourceUrl()).startsWith("http");
        });
        assertThat(result).isSortedAccordingTo((left, right) -> right.publishedAt().compareTo(left.publishedAt()));
    }
}
