package cn.wenchang.brain.eval;

import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.service.WenchangAgentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 可解释的离线评测：检查答案、来源、分类、工具路由和明确无关结果。 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);
    private final WenchangAgentService agentService;
    private final ObjectMapper objectMapper;

    public EvalService(WenchangAgentService agentService) {
        this.agentService = agentService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public EvalReport run() throws IOException {
        List<EvalCase> cases = objectMapper.readValue(
                new ClassPathResource("eval/wenchang_eval.json").getInputStream(), new TypeReference<>() { });
        List<EvalResult> results = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            long started = System.nanoTime();
            try {
                ChatResponseDto response = agentService.chat(evalCase.question(), "eval-" + UUID.randomUUID());
                List<String> failures = evaluate(evalCase, response);
                String status = failures.isEmpty() ? "PASS" : "FAIL";
                results.add(new EvalResult(evalCase.id(), evalCase.question(), response.answer(), response.sources(),
                        response.toolsUsed(), response.latencyMs(), status, String.join("；", failures)));
            } catch (Exception exception) {
                long latency = (System.nanoTime() - started) / 1_000_000;
                results.add(new EvalResult(evalCase.id(), evalCase.question(), "", List.of(), List.of(), latency,
                        "FAIL", exception.getClass().getSimpleName() + ": " + exception.getMessage()));
            }
        }
        int passed = (int) results.stream().filter(result -> "PASS".equals(result.status())).count();
        EvalReport report = new EvalReport(Instant.now(), passed, results.size() - passed, results);
        results.forEach(result -> log.info("[EVAL] {} {} sources={} tools={} latencyMs={} reason={}",
                result.id(), result.status(), result.retrievedSources().size(), result.toolsUsed(),
                result.latencyMs(), result.reason()));
        return report;
    }

    private List<String> evaluate(EvalCase evalCase, ChatResponseDto response) {
        List<String> failures = new ArrayList<>();
        if (response.answer() == null || response.answer().isBlank()) failures.add("没有获得答案");
        if (evalCase.expectSources() && response.sources().isEmpty()) failures.add("未返回知识来源");
        if (evalCase.expectedTool() != null && !evalCase.expectedTool().isBlank()
                && !response.toolsUsed().contains(evalCase.expectedTool())) {
            failures.add("未调用预期工具 " + evalCase.expectedTool());
        }
        if (!evalCase.expectedCategories().isEmpty()) {
            boolean matched = response.sources().stream()
                    .map(source -> source.category() == null ? "" : source.category())
                    .anyMatch(evalCase.expectedCategories()::contains);
            if (!matched) failures.add("未检索到预期分类 " + evalCase.expectedCategories());
        }
        boolean unrelated = response.sources().stream()
                .map(source -> source.category() == null ? "" : source.category())
                .anyMatch(evalCase.forbiddenCategories()::contains);
        if (unrelated) failures.add("检索到明确无关分类 " + evalCase.forbiddenCategories());
        return failures;
    }
}
