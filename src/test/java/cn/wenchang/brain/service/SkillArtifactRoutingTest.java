package cn.wenchang.brain.service;

import cn.wenchang.brain.skill.SkillRegistry;
import cn.wenchang.brain.artifact.ArtifactDescriptor;
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

    @Test
    void explicitNegativeInstructionsOverrideArtifactAndNetworkSkills() {
        var research = skills.require("deep-research");
        var word = skills.require("word-report");
        String request = "梳理文昌红树林保护与修复依据，不要联网，不要生成word文档";

        assertThat(WenchangAgentService.requestedArtifactTool(word, request)).isNull();
        assertThat(WenchangAgentService.explicitlyDeniesArtifacts(request)).isTrue();
        assertThat(WenchangAgentService.explicitlyDeniesNetwork(request)).isTrue();
        assertThat(WenchangAgentService.allowedModelToolNames(research, Set.of(), request))
                .doesNotContain("webSearch", "officialSourceSearch", "collectOfficialMaterials",
                        "createWenchangWordReport", "exportWenchangData", "createStudyTourPackage", "createPolicyBrief");
        assertThat(WenchangAgentService.allowedModelToolNames(word, Set.of(), request))
                .doesNotContain("createWenchangWordReport");
    }

    @Test
    void positiveArtifactRequestStillRoutesNormally() {
        var word = skills.require("word-report");
        assertThat(WenchangAgentService.requestedArtifactTool(word, "生成 Word 报告"))
                .isEqualTo("createWenchangWordReport");
    }
    @Test
    void professionalTitleDoesNotEchoImperativePrompt() {
        var composer = new cn.wenchang.brain.artifact.ArtifactReportComposer();
        assertThat(composer.professionalTitle("请你按高中阶段定向检索教育部门资料，并生成一份带官方来源的文昌市高中名单报告 Word。必须实际生成可下载文件。"))
                .isEqualTo("文昌市高中阶段学校名单与信息核验报告")
                .doesNotContain("请你", "必须", "Word");
    }

    @Test
    void generatedArtifactIsLinkedDirectlyInMarkdownAnswer() {
        var artifact = new ArtifactDescriptor("artifact-1", "conversation-1", "WORD", "专业报告.docx",
                "专业报告", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                4096, "2026-08-12T21:00:00+08:00", "/wenchang-brain/api/artifacts/artifact-1/download",
                false, 3, "wenchang", "public-service");
        assertThat(WenchangAgentService.appendArtifactLinks("报告已整理完成。", java.util.List.of(artifact)))
                .contains("## 已生成文件", "[下载 专业报告](/wenchang-brain/api/artifacts/artifact-1/download)", "3 个来源");
    }
}
