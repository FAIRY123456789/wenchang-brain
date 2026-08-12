package cn.wenchang.brain.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityRouterTest {

    private final CapabilityRouter router = new CapabilityRouter(new TemporalQueryRouter());

    @Test
    void routesEvidenceOfficialTemporalAndOrdinaryQuestions() {
        assertThat(router.route("你刚才的回答依据哪些资料？").toolName()).isEqualTo("knowledgeEvidence");
        assertThat(router.route("查一下有关文昌生态保护的官方资料").toolName())
                .isEqualTo("officialSourceSearch");
        assertThat(router.route("这个数据的官方来源是什么？").toolName())
                .isEqualTo("officialSourceSearch");
        assertThat(router.route("最近一次文昌航天发射是什么时候？").toolName()).isEqualTo("webSearch");
        assertThat(router.route("文昌历史上有哪些重要人物？").required()).isFalse();
    }
}
