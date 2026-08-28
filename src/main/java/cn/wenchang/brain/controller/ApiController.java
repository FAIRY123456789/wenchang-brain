package cn.wenchang.brain.controller;

import cn.wenchang.brain.eval.EvalReport;
import cn.wenchang.brain.eval.EvalService;
import cn.wenchang.brain.model.ChatRequest;
import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.model.KnowledgeStatus;
import cn.wenchang.brain.model.SessionResetRequest;
import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.rag.KnowledgeService;
import cn.wenchang.brain.runtime.RuntimeChatModelProvider;
import cn.wenchang.brain.service.WenchangAgentService;
import cn.wenchang.brain.service.ConversationMemoryService;
import cn.wenchang.brain.service.ConversationService;
import cn.wenchang.brain.tool.WebSearchTool;
import jakarta.validation.Valid;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final WenchangAgentService agentService;
    private final KnowledgeService knowledgeService;
    private final EvalService evalService;
    private final RuntimeChatModelProvider modelProvider;
    private final WenchangProperties properties;
    private final TaskExecutor streamExecutor;
    private final ConversationService conversationService;
    private final ConversationMemoryService conversationMemoryService;
    private final WebSearchTool webSearchTool;

    public ApiController(WenchangAgentService agentService, KnowledgeService knowledgeService, EvalService evalService,
                         RuntimeChatModelProvider modelProvider, WenchangProperties properties,
                         TaskExecutor agentStreamExecutor, ConversationService conversationService,
                         ConversationMemoryService conversationMemoryService, WebSearchTool webSearchTool) {
        this.agentService = agentService;
        this.knowledgeService = knowledgeService;
        this.evalService = evalService;
        this.modelProvider = modelProvider;
        this.properties = properties;
        this.streamExecutor = agentStreamExecutor;
        this.conversationService = conversationService;
        this.conversationMemoryService = conversationMemoryService;
        this.webSearchTool = webSearchTool;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        KnowledgeStatus knowledge = knowledgeService.getStatus();
        var model = modelProvider.current().descriptor();
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "文昌智脑 " + properties.getVersion());
        health.put("time", Instant.now());
        health.put("modelMode", model.mode());
        health.put("provider", model.provider());
        health.put("model", model.model());
        health.put("thinkingEnabled", model.thinkingEnabled());
        health.put("ragStatus", isKnowledgeReady(knowledge) ? "READY" : "NOT_READY");
        health.put("vectorStoreStatus", knowledge.state());
        health.put("knowledgeFiles", knowledge.sourceFiles());
        health.put("chunks", knowledge.chunks());
        var search = webSearchTool.currentHealth();
        health.put("webSearchStatus", properties.getWebSearch().isEnabled() ? search.health() : "DISABLED");
        health.put("webSearchProvider", search.provider());
        health.put("webSearchErrorType", search.errorType());
        return health;
    }

    @PostMapping("/chat")
    public ChatResponseDto chat(@Valid @RequestBody ChatRequest request) {
        ensureModelConfigured();
        var conversation = conversationService.resolveForChat(request.effectiveConversationId(), request.message(),
                request.effectiveAgentId());
        if (request.editMessageId() != null) {
            conversationService.prepareEdit(conversation.id(), request.editMessageId());
            conversationMemoryService.clear(conversation.id());
        }
        conversationMemoryService.ensureRestored(conversation.id());
        Long userMessageId = conversationService.appendUser(conversation.id(), request.message(),
                request.effectiveAgentId(), request.effectiveSkillId(), request.editMessageId());
        ChatResponseDto response = agentService.chat(request.message(), conversation.id(),
                        request.effectiveAgentId(), request.effectiveSkillId())
                .withConversationId(conversation.id());
        conversationService.appendAssistant(conversation.id(), response, userMessageId);
        return response;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        ensureModelConfigured();
        SseEmitter emitter = new SseEmitter(180_000L);
        streamExecutor.execute(() -> {
            try {
                var conversation = conversationService.resolveForChat(request.effectiveConversationId(), request.message(),
                        request.effectiveAgentId());
                if (request.editMessageId() != null) {
                    conversationService.prepareEdit(conversation.id(), request.editMessageId());
                    conversationMemoryService.clear(conversation.id());
                }
                conversationMemoryService.ensureRestored(conversation.id());
                Long userMessageId = conversationService.appendUser(conversation.id(), request.message(),
                        request.effectiveAgentId(), request.effectiveSkillId(), request.editMessageId());
                sendEvent(emitter, "conversation", conversation);
                ChatResponseDto response = agentService.stream(request.message(), conversation.id(),
                        request.effectiveAgentId(), request.effectiveSkillId(),
                        progress -> sendEvent(emitter, "status", progress),
                        chunk -> sendEvent(emitter, "answer_chunk", Map.of("text", chunk)),
                        event -> sendEvent(emitter, event.type(), event.data()))
                        .withConversationId(conversation.id());
                conversationService.appendAssistant(conversation.id(), response, userMessageId);
                sendEvent(emitter, "complete", response);
                emitter.complete();
            } catch (Exception exception) {
                try { sendEvent(emitter, "error", Map.of("message", safeMessage(exception.getMessage()))); }
                catch (Exception ignored) { }
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    @PostMapping("/chat/session/reset")
    public Map<String, Object> resetSession(@Valid @RequestBody SessionResetRequest request) {
        agentService.resetSession(request.sessionId());
        conversationMemoryService.clear(request.sessionId());
        return Map.of("reset", true, "sessionId", request.sessionId());
    }

    @GetMapping("/knowledge/status")
    public KnowledgeStatus knowledgeStatus() { return knowledgeService.getStatus(); }

    @PostMapping("/admin/reindex")
    public KnowledgeStatus reindex() throws IOException { return knowledgeService.reindex(); }

    @PostMapping("/admin/eval")
    public EvalReport eval() throws IOException { return evalService.run(); }

    private boolean isKnowledgeReady(KnowledgeStatus status) {
        return "READY".equals(status.state()) || "LOADED".equals(status.state());
    }

    private void ensureModelConfigured() {
        if (!modelProvider.settingsStatus().configured()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "模型未配置，请进入模型设置");
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            throw new IllegalStateException("流式连接已关闭", exception);
        }
    }

    private String safeMessage(String message) {
        if (message == null) return "请求失败";
        return message.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception exception) {
        HttpStatus status = exception instanceof ResponseStatusException responseStatus
                ? HttpStatus.valueOf(responseStatus.getStatusCode().value()) : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(Map.of(
                "error", exception.getClass().getSimpleName(), "message", safeMessage(exception.getMessage())));
    }
}
