package cn.wenchang.brain.eval;

import java.time.Instant;
import java.util.List;

public record AgentExperienceEvalReport(Instant runAt, int passed, int failed,
                                        List<AgentExperienceEvalResult> results) { }
