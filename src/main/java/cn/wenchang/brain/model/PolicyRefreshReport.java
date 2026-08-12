package cn.wenchang.brain.model;

import java.time.Instant;
import java.util.List;

public record PolicyRefreshReport(
        Instant refreshedAt,
        int topicsChecked,
        int officialCandidates,
        int newCandidates,
        List<String> candidateUrls,
        String reviewPolicy,
        KnowledgeStatus knowledgeStatus
) { }
