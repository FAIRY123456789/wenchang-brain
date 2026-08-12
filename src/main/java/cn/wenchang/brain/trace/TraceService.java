package cn.wenchang.brain.trace;

import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.model.AgentTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 每行一个完整请求，JSONL 适合追加、检索和后续导入分析工具。 */
@Service
public class TraceService {

    private static final Logger log = LoggerFactory.getLogger(TraceService.class);
    private final Path traceFile;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public TraceService(WenchangProperties properties) {
        this.traceFile = Path.of(properties.getTraceFile()).toAbsolutePath();
    }

    public synchronized void append(AgentTrace trace) {
        try {
            Files.createDirectories(traceFile.getParent());
            Files.writeString(traceFile, objectMapper.writeValueAsString(trace) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            log.error("Failed to persist AgentTrace {}", trace.traceId(), exception);
        }
        long failedTools = trace.toolCalls().stream().filter(call -> "FAILED".equals(call.status())).count();
        log.info("[AGENT TRACE] id={} rag={} tools={} failedTools={} llmMs={} totalMs={} sources={}", trace.traceId(),
                trace.ragExecuted(), trace.toolCalls().size(), failedTools, trace.llmLatencyMs(),
                trace.totalLatencyMs(), trace.sources().size());
    }
}
