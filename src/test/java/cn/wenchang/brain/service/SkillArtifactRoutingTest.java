package cn.wenchang.brain.service;

import cn.wenchang.brain.skill.SkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillArtifactRoutingTest {

    private final SkillRegistry skills = new SkillRegistry();

    @Test
    void publicServiceCanContinueToWordOnlyWhenUserExplicitlyRequestsIt() {
        var skill = skills.require("public-service");

        assertThat(WenchangAgentService.allowedModelToolNames(
                skill, Set.of("searchPublicServices"), "查询文昌高中公共服务资料"))
                .isEmpty();

        assertThat(WenchangAgentService.allowedModelToolNames(
                skill, Set.of("searchPublicServices"), "查询文昌高中资料，并生成带官方来源的 Word 报告"))
                .containsExactly("createWenchangWordReport");
        assertThat(WenchangAgentService.requestedArtifactTool(
                skill, "查询文昌高中资料，并生成带官方来源的 Word 报告"))
                .isEqualTo("createWenchangWordReport");
    }

    @Test
    void dataExportDoesNotExposeUnrequestedWordTool() {
        var skill = skills.require("data-export");

        assertThat(WenchangAgentService.allowedModelToolNames(
                skill, Set.of("placeSearch", "exportWenchangData"), "导出文昌研学地点 Excel"))
                .isEmpty();
    }
}
