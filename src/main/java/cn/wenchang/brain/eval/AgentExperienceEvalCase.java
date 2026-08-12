package cn.wenchang.brain.eval;

import java.util.List;

public record AgentExperienceEvalCase(
        String id, String agentId, String skillId, String question,
        List<String> expectedTools, List<String> expectedAnyTools,
        List<String> expectedCategories, int minSources, int minSteps,
        List<String> expectedArtifactTypes, int minArtifacts
) {
    public AgentExperienceEvalCase {
        expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
        expectedAnyTools = expectedAnyTools == null ? List.of() : List.copyOf(expectedAnyTools);
        expectedCategories = expectedCategories == null ? List.of() : List.copyOf(expectedCategories);
        expectedArtifactTypes = expectedArtifactTypes == null ? List.of() : List.copyOf(expectedArtifactTypes);
    }
}
