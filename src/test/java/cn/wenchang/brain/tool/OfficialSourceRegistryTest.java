package cn.wenchang.brain.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialSourceRegistryTest {

    @Test
    void loadsVerifiedRegistryAndPreservesSubdomainPolicy() {
        OfficialSourceRegistry registry = new OfficialSourceRegistry("data/official-source-registry.json");

        assertThat(registry.sources()).isNotEmpty().allSatisfy(source -> {
            assertThat(source.name()).isNotBlank();
            assertThat(source.domain()).doesNotContain("/", "://");
            assertThat(source.level()).isIn("P0", "P1", "P2", "P3");
        });
        assertThat(registry.sources()).anySatisfy(source -> {
            assertThat(source.domain()).isEqualTo("stats.hainan.gov.cn");
            assertThat(source.includeSubdomains()).isFalse();
        });
    }
}
