package cn.wenchang.brain.eval;

import cn.wenchang.brain.model.SourceRef;

import java.util.List;

public record EvalResult(
        String id,
        String question,
        String answer,
        List<SourceRef> retrievedSources,
        List<String> toolsUsed,
        long latencyMs,
        String status,
        String reason
) { }
