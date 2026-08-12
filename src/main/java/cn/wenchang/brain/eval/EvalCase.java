package cn.wenchang.brain.eval;

import java.util.List;

public record EvalCase(
        String id,
        String question,
        boolean expectSources,
        String expectedTool,
        List<String> expectedCategories,
        List<String> forbiddenCategories
) {
    public EvalCase {
        expectedCategories = expectedCategories == null ? List.of() : List.copyOf(expectedCategories);
        forbiddenCategories = forbiddenCategories == null ? List.of() : List.copyOf(forbiddenCategories);
    }
}
