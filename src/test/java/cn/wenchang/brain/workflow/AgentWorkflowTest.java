package cn.wenchang.brain.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentWorkflowTest {

    @Test
    void deepResearchCreatesPublicFourToSixStepPlan() {
        AgentRunPlan plan = new DeepResearchWorkflow().plan(
                new DeepResearchWorkflow.ResearchRequest("文昌商业航天与城市发展有什么关系？", true, true));

        assertThat(plan.workflowType()).isEqualTo("deep-research");
        assertThat(plan.steps()).hasSizeBetween(4, 6);
        assertThat(plan.steps()).extracting(AgentRunStep::tools)
                .anySatisfy(tools -> assertThat(tools).contains("knowledgeEvidence"))
                .anySatisfy(tools -> assertThat(tools).contains("officialSourceSearch"))
                .anySatisfy(tools -> assertThat(tools).contains("webSearch"));
    }

    @Test
    void studyTourUsesCoordinatesAndDoesNotInventDrivingTimes() {
        StudyTourPlanningService service = new StudyTourPlanningService();
        List<StudyTourPlanningService.StudyTourPlace> places = List.of(
                place("launch", "航天科普中心", 19.61, 110.95, List.of("航天")),
                place("coast", "海岸生态观察点", 19.62, 110.96, List.of("生态")),
                place("far", "文化学习点", 19.25, 110.48, List.of("文化"))
        );

        var result = service.plan(new StudyTourPlanningService.StudyTourRequest(
                "初中生", List.of("航天", "生态"), 3), places);

        assertThat(result.stops()).extracting(StudyTourPlanningService.StudyTourStop::place)
                .containsExactly("航天科普中心", "海岸生态观察点");
        assertThat(result.notes()).allSatisfy(note -> assertThat(note).doesNotContain("分钟", "驾车约"));
        assertThat(result.notes()).anySatisfy(note -> assertThat(note).contains("不代表精确道路距离"));
    }

    private StudyTourPlanningService.StudyTourPlace place(String id, String name, double latitude,
                                                           double longitude, List<String> themes) {
        return new StudyTourPlanningService.StudyTourPlace(id, name, "龙楼镇", latitude, longitude, themes,
                List.of("初中生"), List.of("观察", "记录"), "文昌市人民政府", "https://wenchang.hainan.gov.cn/");
    }
}
