package cn.wenchang.brain.eval;

import java.time.Instant;
import java.util.List;

public record EvalReport(Instant runAt, int passed, int failed, List<EvalResult> results) { }
