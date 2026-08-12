package cn.wenchang.brain.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ArtifactReportComposerTest {

    private final ArtifactReportComposer composer = new ArtifactReportComposer();

    @Test
    void turnsWrappedMcpJsonIntoProfessionalHighSchoolReport() {
        String wrapped = "[{\"text\":\"{\\\"tool\\\":\\\"searchPublicServices\\\",\\\"count\\\":3,"
                + "\\\"results\\\":[{\\\"id\\\":\\\"middle\\\",\\\"name\\\":\\\"文昌中学\\\","
                + "\\\"town\\\":\\\"文城镇\\\",\\\"address\\\":\\\"文建路\\\","
                + "\\\"serviceScope\\\":\\\"中学教育\\\",\\\"sourceOrganization\\\":\\\"文昌市教育局\\\","
                + "\\\"sourceUrl\\\":\\\"https://wenchang.hainan.gov.cn/school\\\"},"
                + "{\\\"id\\\":\\\"primary\\\",\\\"name\\\":\\\"文昌市第三小学\\\","
                + "\\\"sourceUrl\\\":\\\"https://example.gov.cn/primary\\\"},"
                + "{\\\"id\\\":\\\"college\\\",\\\"name\\\":\\\"海南外国语职业学院\\\","
                + "\\\"sourceUrl\\\":\\\"https://example.edu.cn/college\\\"},"
                + "{\\\"id\\\":\\\"affiliated\\\",\\\"name\\\":\\\"清华大学附属中学文昌学校\\\","
                + "\\\"sourceUrl\\\":\\\"https://example.edu.cn/affiliated\\\"}]}\"}]";

        var report = composer.compose("请按高中阶段检索教育部门资料，并生成一份带官方来源的名单 Word",
                Map.of("searchPublicServices", wrapped));

        assertThat(report.title()).isEqualTo("文昌市高中阶段学校名单与信息核验报告");
        assertThat(report.content()).contains("|1|文昌中学|", "清华大学附属中学文昌学校",
                        "学校名称含“中学”不等同于已确认开设高中学段")
                .doesNotContain("文昌市第三小学", "海南外国语职业学院", "searchPublicServices", "\\\"results\\\"");
        assertThat(report.sources()).containsExactly(
                "文昌市教育局 - https://wenchang.hainan.gov.cn/school",
                "公开来源 - https://example.edu.cn/affiliated");
    }
}
