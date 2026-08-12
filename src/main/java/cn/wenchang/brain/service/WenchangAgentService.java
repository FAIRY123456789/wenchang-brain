package cn.wenchang.brain.service;

import cn.wenchang.brain.artifact.ArtifactService;
import cn.wenchang.brain.artifact.ArtifactReportComposer;
import cn.wenchang.brain.agent.AgentProfile;
import cn.wenchang.brain.agent.AgentProfileRegistry;
import cn.wenchang.brain.agent.CapabilityRouter;
import cn.wenchang.brain.agent.WenchangToolRegistry;
import cn.wenchang.brain.model.AgentProgress;
import cn.wenchang.brain.model.AgentApprovalRequest;
import cn.wenchang.brain.model.AgentRunEvent;
import cn.wenchang.brain.model.AgentRunStep;
import cn.wenchang.brain.model.AgentRunSummary;
import cn.wenchang.brain.model.AgentTrace;
import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.model.ToolCallTrace;
import cn.wenchang.brain.rag.RagService;
import cn.wenchang.brain.runtime.RuntimeChatModelProvider;
import cn.wenchang.brain.skill.SkillDefinition;
import cn.wenchang.brain.skill.SkillRegistry;
import cn.wenchang.brain.skill.WorkflowType;
import cn.wenchang.brain.tool.PlaceSearchTool;
import cn.wenchang.brain.trace.ToolTraceCollector;
import cn.wenchang.brain.trace.TraceService;
import cn.wenchang.brain.workflow.DeepResearchWorkflow;
import cn.wenchang.brain.workflow.StudyTourPlanningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import cn.wenchang.brain.artifact.ArtifactDescriptor;
import java.util.function.Consumer;

/** V1.4 Agent 编排：Profile + Skill + RAG + Native/MCP Tool + 可公开 Agent Run + Trace。 */
@Service
public class WenchangAgentService {

    private final RuntimeChatModelProvider modelProvider;
    private final RagService ragService;
    private final CapabilityRouter capabilityRouter;
    private final WenchangToolRegistry toolRegistry;
    private final TraceService traceService;
    private final ChatMemory chatMemory;
    private final AgentProfileRegistry profileRegistry;
    private final SkillRegistry skillRegistry;
    private final DeepResearchWorkflow deepResearchWorkflow;
    private final StudyTourPlanningService studyTourPlanningService;
    private final PlaceSearchTool placeSearchTool;
    private final AgentRunPersistenceService agentRunPersistenceService;
    private final AgentApprovalService agentApprovalService;
    private final ArtifactService artifactService;
    private final ArtifactReportComposer artifactReportComposer;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public WenchangAgentService(RuntimeChatModelProvider modelProvider, RagService ragService,
                                CapabilityRouter capabilityRouter, WenchangToolRegistry toolRegistry,
                                TraceService traceService, ChatMemory chatMemory,
                                AgentProfileRegistry profileRegistry, SkillRegistry skillRegistry,
                                DeepResearchWorkflow deepResearchWorkflow,
                                StudyTourPlanningService studyTourPlanningService,
                                PlaceSearchTool placeSearchTool,
                                AgentRunPersistenceService agentRunPersistenceService,
                                AgentApprovalService agentApprovalService,
                                ArtifactService artifactService,
                                ArtifactReportComposer artifactReportComposer) {
        this.modelProvider = modelProvider;
        this.ragService = ragService;
        this.capabilityRouter = capabilityRouter;
        this.toolRegistry = toolRegistry;
        this.traceService = traceService;
        this.chatMemory = chatMemory;
        this.profileRegistry = profileRegistry;
        this.skillRegistry = skillRegistry;
        this.deepResearchWorkflow = deepResearchWorkflow;
        this.studyTourPlanningService = studyTourPlanningService;
        this.placeSearchTool = placeSearchTool;
        this.agentRunPersistenceService = agentRunPersistenceService;
        this.agentApprovalService = agentApprovalService;
        this.artifactService = artifactService;
        this.artifactReportComposer = artifactReportComposer;
    }

    public ChatResponseDto chat(String message, String requestedSessionId) {
        return chat(message, requestedSessionId, AgentProfileRegistry.DEFAULT_AGENT_ID, null);
    }

    public ChatResponseDto chat(String message, String requestedSessionId, String agentId, String skillId) {
        return execute(message, requestedSessionId, agentId, skillId, false,
                ignored -> { }, ignored -> { }, ignored -> { });
    }

    public ChatResponseDto stream(String message, String requestedSessionId,
                                  Consumer<AgentProgress> progressConsumer,
                                  Consumer<String> chunkConsumer) {
        return stream(message, requestedSessionId, AgentProfileRegistry.DEFAULT_AGENT_ID, null,
                progressConsumer, chunkConsumer, ignored -> { });
    }

    public ChatResponseDto stream(String message, String requestedSessionId, String agentId, String skillId,
                                  Consumer<AgentProgress> progressConsumer, Consumer<String> chunkConsumer,
                                  Consumer<AgentRunEvent> eventConsumer) {
        return execute(message, requestedSessionId, agentId, skillId, true,
                progressConsumer, chunkConsumer, eventConsumer);
    }

    public void resetSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) chatMemory.clear(sessionId);
    }

    private ChatResponseDto execute(String message, String requestedSessionId, String requestedAgentId,
                                    String requestedSkillId, boolean streaming,
                                    Consumer<AgentProgress> progressConsumer,
                                    Consumer<String> chunkConsumer,
                                    Consumer<AgentRunEvent> eventConsumer) {
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? UUID.randomUUID().toString() : requestedSessionId;
        AgentProfile profile = profileRegistry.resolve(requestedAgentId);
        SkillDefinition skill = requestedSkillId == null || requestedSkillId.isBlank()
                ? null : skillRegistry.require(requestedSkillId);
        String traceId = UUID.randomUUID().toString();
        Instant runStartedAt = Instant.now();
        long totalStarted = System.nanoTime();
        long llmLatency = 0;
        RagService.RagResult rag = new RagService.RagResult(List.of(), List.of(), List.of(), 0);
        String answer = "";
        String error = null;
        List<ToolCallTrace> calls;
        List<String> toolsUsed;
        List<String> artifactIds = List.of();
        RuntimeChatModelProvider.ModelHandle modelHandle = modelProvider.current();
        RuntimeChatModelProvider.ModelDescriptor descriptor = modelHandle.descriptor();
        CapabilityRouter.RoutingDecision decision = capabilityRouter.route(message);
        List<RunStepState> runSteps = buildPlan(message, skill, decision);

        ApprovalIntent approvalIntent = approvalIntent(message);
        if (approvalIntent != null) {
            return approvalRequired(message, sessionId, profile, skill, descriptor, traceId, runStartedAt,
                    approvalIntent, streaming, chunkConsumer, eventConsumer);
        }

        emit(eventConsumer, "agent_selected", Map.of("agentId", profile.id(), "displayName", profile.displayName(),
                "icon", profile.icon()));
        if (skill != null) emit(eventConsumer, "skill_selected", Map.of("skillId", skill.id(),
                "displayName", skill.displayName(), "command", skill.command()));
        emit(eventConsumer, "plan_created", Map.of("steps", runSteps.stream().map(RunStepState::publicView).toList()));

        ToolTraceCollector.begin(traceId);
        Set<String> prefetchedTools = new LinkedHashSet<>();
        Map<String, String> toolOutputs = new LinkedHashMap<>();
        List<String> knowledgeCategories = skill != null && !skill.preferredCategories().isEmpty()
                ? skill.preferredCategories() : profile.knowledgeCategories();
        boolean skillCategoryConstraint = skill != null && !skill.preferredCategories().isEmpty();
        try {
            RunStepState retrievalStep = step(runSteps, "knowledge");
            startStep(retrievalStep, eventConsumer);
            progressConsumer.accept(AgentProgress.of("retrieval", "正在检索文昌知识库"));
            rag = ragService.retrieve(message, knowledgeCategories, skillCategoryConstraint);
            progressConsumer.accept(AgentProgress.found(rag.chunks().size()));
            if (!rag.sources().isEmpty()) emit(eventConsumer, "source_found", Map.of(
                    "stepId", retrievalStep.id, "count", rag.sources().size()));
            completeStep(retrievalStep, rag.latencyMs(), rag.sources().size(), eventConsumer);

            List<String> toolsToRun = toolsFor(skill, decision, message);
            for (String toolName : toolsToRun) {
                RunStepState toolStep = step(runSteps, "tool-" + toolName);
                startStep(toolStep, eventConsumer);
                progressConsumer.accept(AgentProgress.of(progressStage(toolName), progressMessage(toolName)));
                emit(eventConsumer, "tool_started", Map.of("stepId", toolStep.id,
                        "toolName", toolName, "label", toolStep.label));
                long toolStarted = System.nanoTime();
                try {
                    Map<String, Object> toolArguments = arguments(toolName, message, sessionId,
                            profile.id(), skill == null ? "" : skill.id(), toolOutputs, rag.sources());
                    toolStep.toolSource = toolSource(toolName);
                    toolStep.inputPreview = preview(mapper.writeValueAsString(toolArguments));
                    String output = toolRegistry.invoke(toolName, toolArguments, traceId, Map.of(
                            "wenchang.conversationId", sessionId,
                            "wenchang.agentId", profile.id(),
                            "wenchang.skillId", skill == null ? "" : skill.id()));
                    prefetchedTools.add(toolName);
                    toolOutputs.put(toolName, output);
                    toolStep.summary = preview(output);
                    long latency = (System.nanoTime() - toolStarted) / 1_000_000;
                    emit(eventConsumer, "tool_completed", Map.of("stepId", toolStep.id,
                            "toolName", toolName, "status", "completed", "latencyMs", latency));
                    completeStep(toolStep, latency, 0, eventConsumer);
                } catch (Exception exception) {
                    long latency = (System.nanoTime() - toolStarted) / 1_000_000;
                    String failure = "工具暂不可用：" + safeMessage(exception.getMessage());
                    toolOutputs.put(toolName, failure);
                    toolStep.status = "failed";
                    toolStep.latencyMs = latency;
                    toolStep.toolSource = toolSource(toolName);
                    toolStep.errorType = exception.getClass().getSimpleName();
                    toolStep.errorMessage = safeMessage(exception.getMessage());
                    emit(eventConsumer, "tool_completed", Map.of("stepId", toolStep.id,
                            "toolName", toolName, "status", "failed", "latencyMs", latency));
                    emit(eventConsumer, "step_completed", toolStep.eventData());
                }
            }

            if (skill != null && skill.workflowType() == WorkflowType.STUDY_TOUR_PLANNING) {
                toolOutputs.put("studyTourPlanning", studyTourPlan(message));
            }

            RunStepState answerStep = step(runSteps, "answer");
            startStep(answerStep, eventConsumer);
            String augmentedQuestion = augmentWithToolResults(message, toolOutputs);
            progressConsumer.accept(AgentProgress.of("generation", "正在组织回答 · " + profile.displayName()));
            long llmStarted = System.nanoTime();
            ChatClient.ChatClientRequestSpec request = modelHandle.chatClient().prompt()
                    .system(profile.systemInstruction() + "\n输出风格：" + profile.responseStyle()
                            + (skill == null ? "" : "\n当前技能：" + skill.systemInstruction()))
                    .advisors(ragService.advisorFor(message, knowledgeCategories, skillCategoryConstraint))
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .tools(modelCallbacks(skill, prefetchedTools, message).toArray())
                    .toolContext(Map.of(ToolTraceCollector.TRACE_ID_CONTEXT_KEY, traceId,
                            "wenchang.conversationId", sessionId,
                            "wenchang.agentId", profile.id(),
                            "wenchang.skillId", skill == null ? "" : skill.id()))
                    .user(augmentedQuestion);

            if (streaming && !"UNCONFIGURED".equals(descriptor.mode())) {
                StringBuilder streamed = new StringBuilder();
                try {
                    request.stream().content().doOnNext(chunk -> {
                        if (chunk != null && !chunk.isEmpty()) {
                            streamed.append(chunk);
                            chunkConsumer.accept(chunk);
                        }
                    }).blockLast();
                    answer = streamed.toString();
                } catch (Exception exception) {
                    if (!isStreamingUnsupported(exception)) throw exception;
                    answer = request.call().content();
                    if (answer != null && !answer.isEmpty()) chunkConsumer.accept(answer);
                }
            } else if (streaming) {
                answer = request.call().content();
                if (answer != null && !answer.isEmpty()) chunkConsumer.accept(answer);
            } else {
                answer = request.call().content();
            }
            llmLatency = (System.nanoTime() - llmStarted) / 1_000_000;
            completeStep(answerStep, llmLatency, rag.sources().size(), eventConsumer);
            progressConsumer.accept(AgentProgress.of("completed", "回答完成"));
        } catch (Exception exception) {
            error = exception.getClass().getSimpleName() + ": " + safeMessage(exception.getMessage());
            answer = "本次任务未能完成，请进入模型设置检查配置。traceId：" + traceId;
            if (streaming) chunkConsumer.accept(answer);
        } finally {
            calls = ToolTraceCollector.snapshot(traceId);
            artifactIds = ToolTraceCollector.artifactIds(traceId);
            toolsUsed = calls.stream().map(ToolCallTrace::toolName)
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toCollection(LinkedHashSet::new), List::copyOf));
            long totalLatency = (System.nanoTime() - totalStarted) / 1_000_000;
            traceService.append(new AgentTrace(traceId, Instant.now(), sessionId, message, true,
                    rag.chunks(), calls, rag.latencyMs(), llmLatency, totalLatency, rag.sources(),
                    descriptor.mode(), descriptor.provider(), descriptor.model(), preview(answer), error));
            ToolTraceCollector.clear(traceId);
        }
        long totalLatency = (System.nanoTime() - totalStarted) / 1_000_000;
        List<ArtifactDescriptor> artifacts = artifactService.descriptorsByIds(artifactIds);
        boolean artifactToolCalled = toolsUsed.stream().anyMatch(this::isArtifactTool);
        answer = appendArtifactLinks(enforceArtifactTruth(answer, artifactToolCalled, artifacts), artifacts);
        artifacts.forEach(artifact -> emit(eventConsumer, "artifact_created", artifactEvent(artifact)));
        if (!artifacts.isEmpty()) {
            RunStepState artifactStep = runSteps.stream().filter(item ->
                    item.toolName != null && item.toolName.matches("createWenchangWordReport|exportWenchangData|createStudyTourPackage|createPolicyBrief"))
                    .reduce((first, second) -> second).orElse(null);
            String actualTool = calls.stream().map(ToolCallTrace::toolName).filter(this::isArtifactTool)
                    .reduce((first, second) -> second).orElse("createWenchangWordReport");
            if (artifactStep == null) {
                artifactStep = new RunStepState("tool-" + actualTool, toolLabel(actualTool), "tool", actualTool);
                runSteps.add(artifactStep);
            }
            artifactStep.status = "completed";
            artifactStep.toolSource = "MCP";
            artifactStep.artifactIds = artifacts.stream().map(ArtifactDescriptor::id).toList();
            artifactStep.sourceCount = artifacts.stream().mapToInt(ArtifactDescriptor::sourceCount).sum();
            artifactStep.summary = artifacts.stream().map(ArtifactDescriptor::filename)
                    .collect(java.util.stream.Collectors.joining("、"));
        }
        AgentRunSummary run = new AgentRunSummary(profile.id(), profile.displayName(),
                skill == null ? null : skill.id(), skill == null ? null : skill.displayName(),
                runSteps.stream().map(RunStepState::summary).toList(), toolsUsed.size(), rag.sources().size(), totalLatency,
                null, null, null, null, artifacts);
        try {
            var persisted = agentRunPersistenceService.persist(sessionId, message, run);
            run = new AgentRunSummary(run.agentId(), run.agentName(), run.skillId(), run.skillName(), run.steps(),
                    run.toolCount(), run.sourceCount(), run.latencyMs(), persisted.id(), persisted.status(),
                    runStartedAt, persisted.completedAt(), artifacts);
        } catch (RuntimeException persistenceFailure) {
            run = new AgentRunSummary(run.agentId(), run.agentName(), run.skillId(), run.skillName(), run.steps(),
                    run.toolCount(), run.sourceCount(), run.latencyMs(), null, "PERSISTENCE_FAILED",
                    runStartedAt, Instant.now(), artifacts);
        }
        return new ChatResponseDto(answer == null ? "" : answer, rag.sources(), toolsUsed, traceId, totalLatency,
                descriptor.mode(), descriptor.provider(), descriptor.model(), null, profile.id(),
                skill == null ? null : skill.id(), run, artifacts);
    }

    private List<RunStepState> buildPlan(String message, SkillDefinition skill,
                                         CapabilityRouter.RoutingDecision decision) {
        List<RunStepState> result = new ArrayList<>();
        result.add(new RunStepState("knowledge", "检索文昌知识库", "retrieval", ""));
        if (skill != null && skill.workflowType() == WorkflowType.DEEP_RESEARCH) {
            var plan = deepResearchWorkflow.plan(new DeepResearchWorkflow.ResearchRequest(message, true, true));
            for (var item : plan.steps()) {
                if ("knowledge".equals(item.id()) || "answer".equals(item.id())) continue;
                String tool = item.tools().isEmpty() ? "" : item.tools().get(0);
                if (!tool.isBlank()) result.add(new RunStepState("tool-" + tool, item.title(), item.stage(), tool));
            }
        } else if (skill != null) {
            for (String tool : toolsFor(skill, decision, message)) result.add(new RunStepState("tool-" + tool,
                    toolLabel(tool), "tool", tool));
        } else if (decision.required()) {
            result.add(new RunStepState("tool-" + decision.toolName(), toolLabel(decision.toolName()),
                    "tool", decision.toolName()));
        }
        result.add(new RunStepState("answer", "整理来源并生成结果", "answer", ""));
        return result;
    }

    private ChatResponseDto approvalRequired(String message, String sessionId, AgentProfile profile,
                                             SkillDefinition skill,
                                             RuntimeChatModelProvider.ModelDescriptor descriptor,
                                             String traceId, Instant startedAt, ApprovalIntent intent,
                                             boolean streaming, Consumer<String> chunks,
                                             Consumer<AgentRunEvent> events) {
        var approval = agentApprovalService.preview(new AgentApprovalRequest(null, sessionId, profile.id(),
                skill == null ? null : skill.id(), intent.actionType(), intent.operation(), intent.impactScope(),
                Map.of("requestedMessage", message)));
        Map<String, Object> approvalData = new LinkedHashMap<>();
        approvalData.put("approvalId", approval.id());
        approvalData.put("actionType", approval.actionType());
        approvalData.put("operation", approval.operation());
        approvalData.put("impactScope", approval.impactScope());
        approvalData.put("status", approval.status());
        emit(events, "approval_required", approvalData);
        String answer = "该操作会修改长期系统资产，已准备执行预览，等待你的确认。\n\n"
                + "- 操作：" + intent.operation() + "\n"
                + "- 影响范围：" + intent.impactScope() + "\n"
                + "- 当前状态：等待确认";
        if (streaming) chunks.accept(answer);
        long latency = Math.max(0, java.time.Duration.between(startedAt, Instant.now()).toMillis());
        AgentRunStep approvalStep = new AgentRunStep("approval", "等待用户确认", "approval", "",
                "waiting_approval", latency, 0, null, "长期资产操作已生成预览", null, null, null);
        AgentRunSummary run = new AgentRunSummary(profile.id(), profile.displayName(),
                skill == null ? null : skill.id(), skill == null ? null : skill.displayName(),
                List.of(approvalStep), 0, 0, latency, null, "WAITING_APPROVAL", startedAt, null);
        try {
            var persisted = agentRunPersistenceService.persist(sessionId, message, run);
            run = new AgentRunSummary(run.agentId(), run.agentName(), run.skillId(), run.skillName(), run.steps(),
                    0, 0, latency, persisted.id(), "WAITING_APPROVAL", startedAt, null);
        } catch (RuntimeException ignored) { }
        traceService.append(new AgentTrace(traceId, Instant.now(), sessionId, message, false, List.of(), List.of(),
                0, 0, latency, List.of(), descriptor.mode(), descriptor.provider(), descriptor.model(),
                preview(answer), null));
        return new ChatResponseDto(answer, List.of(), List.of(), traceId, latency, descriptor.mode(),
                descriptor.provider(), descriptor.model(), null, profile.id(),
                skill == null ? null : skill.id(), run);
    }

    private ApprovalIntent approvalIntent(String message) {
        if (message == null) return null;
        if (message.matches(".*(重新索引|重建索引).*")) {
            return new ApprovalIntent("REINDEX_KNOWLEDGE", "重新索引文昌知识库", "全部 active 知识文档与本地向量索引");
        }
        if (message.matches(".*(批量更新政策知识|刷新政策知识|更新政策库).*")) {
            return new ApprovalIntent("REFRESH_POLICIES", "发现政策候选并重新索引", "政策候选清单与知识库索引");
        }
        if (message.matches(".*(加入知识库|导入知识库).*")) {
            return new ApprovalIntent("ADD_TO_KNOWLEDGE", "加入长期知识库", "正式知识语料、来源索引与向量索引");
        }
        if (message.matches(".*(删除数据|批量删除).*")) {
            return new ApprovalIntent("DELETE_DATA", "删除长期系统数据", "请求中指定的持久数据；执行前仍需精确目标");
        }
        return null;
    }

    private List<String> toolsFor(SkillDefinition skill, CapabilityRouter.RoutingDecision decision, String message) {
        if (skill != null) {
            Set<String> tools = new LinkedHashSet<>(skill.requiredTools());
            String artifactTool = requestedArtifactTool(skill, message);
            if (artifactTool != null) tools.add(artifactTool);
            return List.copyOf(tools);
        }
        return decision.required() ? List.of(decision.toolName()) : List.of();
    }

    private Map<String, Object> arguments(String toolName, String message, String conversationId,
                                          String agentId, String skillId, Map<String, String> priorOutputs,
                                          List<cn.wenchang.brain.model.SourceRef> sources) {
        return switch (toolName) {
            case "webSearch" -> Map.of("query", message);
            case "officialSourceSearch", "knowledgeEvidence" -> Map.of("query", message);
            case "placeSearch" -> Map.of("keyword", "", "category", "", "town", extractTown(message),
                    "theme", message, "ageGroup", extractAgeGroup(message));
            case "policySearch" -> Map.of("query", message, "category", "", "status", "");
            case "searchPublicServices" -> Map.of("keyword", resourceKeyword(message),
                    "category", resourceCategory(message), "town", extractTown(message));
            case "searchTownshipProfile" -> Map.of("town", extractTown(message));
            case "searchStudyTourPlaces" -> Map.of("theme", message, "town", extractTown(message),
                    "ageGroup", extractAgeGroup(message));
            case "collectOfficialMaterials" -> Map.of("topic", message,
                    "categories", List.of(dominantTheme(message)), "maxSources", 8);
            case "createWenchangWordReport" -> wordReportArguments(message, conversationId, agentId, skillId,
                    priorOutputs, sources);
            case "exportWenchangData" -> Map.of("datasetType", datasetType(message), "fields", List.of(),
                    "filters", exportFilters(message), "format", message.toLowerCase(Locale.ROOT).contains("csv") ? "csv" : "xlsx",
                    "conversationId", conversationId, "createdByAgent", agentId, "skillId", skillId);
            case "createStudyTourPackage" -> Map.of("ageGroup", extractAgeGroup(message),
                    "duration", extractDuration(message), "themes", List.of(dominantTheme(message)),
                    "preferences", List.of(message), "conversationId", conversationId,
                    "createdByAgent", agentId, "skillId", skillId);
            case "createPolicyBrief" -> Map.of("topic", message, "timeRange", extractTimeRange(message),
                    "focus", dominantTheme(message), "conversationId", conversationId,
                    "createdByAgent", agentId, "skillId", skillId);
            default -> Map.of("query", message);
        };
    }

    private String datasetType(String message) {
        if (message.matches(".*(政策|条例|规划).*")) return "policies";
        if (message.matches(".*(公共服务|医院|学校|场馆|政务).*")) return "publicServices";
        if (message.matches(".*(来源|资料目录).*")) return "sources";
        return "places";
    }

    private Map<String, Object> exportFilters(String message) {
        Map<String, Object> filters = new LinkedHashMap<>();
        String town = extractTown(message);
        if (!town.isBlank()) filters.put("town", town);
        if (message.matches(".*(学校|教育|高中|初中|小学).*")) filters.put("category", "education");
        if (message.matches(".*(高中|高一|高二|高三|初中|中学).*")) filters.put("name", "中学");
        else if (message.matches(".*(小学|小学生).*")) filters.put("name", "小学");
        else if (message.matches(".*(医院|医疗|卫生).*")) filters.put("category", "medical");
        return Map.copyOf(filters);
    }

    private String extractDuration(String message) {
        if (message.matches(".*(半天|半日).*")) return "半天";
        if (message.matches(".*(两天|2天|二日|两日).*")) return "两天";
        if (message.matches(".*(多日|三天|3天).*")) return "多日";
        return "一天";
    }

    private String extractTimeRange(String message) {
        java.util.regex.Matcher range = java.util.regex.Pattern.compile("(20\\d{2})\\s*[-至到—]\\s*(20\\d{2})").matcher(message);
        if (range.find()) return range.group(1) + "-" + range.group(2);
        java.util.regex.Matcher year = java.util.regex.Pattern.compile("20\\d{2}").matcher(message);
        return year.find() ? year.group() : "";
    }

    private String artifactTitle(String message, String fallback) {
        String title = artifactReportComposer.professionalTitle(message);
        return title == null || title.isBlank() ? fallback : title;
    }

    private Map<String, Object> wordReportArguments(String message, String conversationId,
                                                     String agentId, String skillId,
                                                     Map<String, String> priorOutputs,
                                                     List<cn.wenchang.brain.model.SourceRef> ragSources) {
        ArtifactReportComposer.ComposedReport report = artifactReportComposer.compose(message, priorOutputs);
        List<String> reportSources = report.sources();
        if (reportSources.isEmpty()) {
            reportSources = ragSources.stream().map(cn.wenchang.brain.model.SourceRef::sourceUrl)
                    .filter(url -> url != null && !url.isBlank()).distinct().toList();
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("title", report.title());
        arguments.put("topic", report.topic());
        arguments.put("content", report.content());
        arguments.put("sources", reportSources);
        arguments.put("conversationId", conversationId);
        arguments.put("createdByAgent", agentId);
        arguments.put("skillId", skillId);
        return Map.copyOf(arguments);
    }

    private String studyTourPlan(String message) {
        try {
            List<PlaceSearchTool.PlaceItem> places = placeSearchTool.search("", "", extractTown(message),
                    dominantTheme(message), extractAgeGroup(message), 8);
            List<StudyTourPlanningService.StudyTourPlace> candidates = places.stream().map(item ->
                    new StudyTourPlanningService.StudyTourPlace(item.id(), item.name(), item.town(),
                            item.latitude(), item.longitude(), item.themes(), item.ageGroups(), item.learningPoints(),
                            item.sourceOrganization(), item.sourceUrl())).toList();
            var plan = studyTourPlanningService.plan(new StudyTourPlanningService.StudyTourRequest(
                    extractAgeGroup(message), List.of(dominantTheme(message)), 3), candidates);
            return mapper.writeValueAsString(plan);
        } catch (Exception exception) {
            return "研学路线生成失败：" + safeMessage(exception.getMessage());
        }
    }

    private String augmentWithToolResults(String message, Map<String, String> outputs) {
        if (outputs.isEmpty()) return message;
        StringBuilder result = new StringBuilder(message).append("\n\n以下是本次公开任务步骤取得的工具结果。")
                .append("请核对日期和来源，结果为空或失败时如实说明：\n");
        outputs.forEach((name, output) -> result.append("\n### ").append(name).append("\n").append(output).append('\n'));
        return result.toString();
    }

    private RunStepState step(List<RunStepState> steps, String id) {
        return steps.stream().filter(item -> item.id.equals(id)).findFirst()
                .orElseGet(() -> { RunStepState item = new RunStepState(id, id, "task", ""); steps.add(item); return item; });
    }

    private void startStep(RunStepState step, Consumer<AgentRunEvent> events) {
        step.status = "running";
        emit(events, "step_started", step.eventData());
    }

    private void completeStep(RunStepState step, long latencyMs, int sourceCount,
                              Consumer<AgentRunEvent> events) {
        step.status = "completed";
        step.latencyMs = latencyMs;
        step.sourceCount = sourceCount;
        emit(events, "step_completed", step.eventData());
    }

    private void emit(Consumer<AgentRunEvent> consumer, String type, Map<String, Object> data) {
        consumer.accept(AgentRunEvent.of(type, data));
    }

    private String progressStage(String toolName) {
        return switch (toolName) {
            case "webSearch" -> "web_search";
            case "officialSourceSearch" -> "official_search";
            case "knowledgeEvidence" -> "knowledge_evidence";
            case "placeSearch" -> "place_search";
            case "policySearch" -> "policy_search";
            default -> "tool_call";
        };
    }

    private String progressMessage(String toolName) { return "正在" + toolLabel(toolName); }

    private String toolSource(String toolName) {
        boolean mcp = toolRegistry.catalog().mcpTools().stream().anyMatch(item ->
                item.name().equals(toolName) || item.name().endsWith("_" + toolName));
        return mcp ? "MCP" : "NATIVE";
    }

    private String toolLabel(String toolName) {
        return switch (toolName) {
            case "webSearch" -> "联网搜索近期信息";
            case "officialSourceSearch" -> "查询权威来源";
            case "knowledgeEvidence" -> "核验知识证据";
            case "placeSearch" -> "查询研学地点";
            case "policySearch" -> "查询政策资料";
            case "collectOfficialMaterials" -> "采集专题公开资料";
            case "createWenchangWordReport" -> "生成 Word 报告";
            case "exportWenchangData" -> "导出数据表";
            case "createStudyTourPackage" -> "生成研学方案文件";
            case "createPolicyBrief" -> "生成政策简报文件";
            case "searchPublicServices" -> "查询公共服务资源";
            case "searchTownshipProfile" -> "查询乡镇资料";
            case "searchStudyTourPlaces" -> "查询研学资源";
            default -> "调用智能体工具";
        };
    }

    private boolean isArtifactTool(String toolName) {
        return toolName != null && toolName.matches(
                "createWenchangWordReport|exportWenchangData|createStudyTourPackage|createPolicyBrief");
    }

    private List<org.springframework.ai.tool.ToolCallback> modelCallbacks(SkillDefinition skill,
                                                                          Set<String> prefetchedTools,
                                                                          String message) {
        if (skill == null) return toolRegistry.callbacksExcluding(prefetchedTools);
        Set<String> allowed = allowedModelToolNames(skill, prefetchedTools, message);
        return toolRegistry.callbacksNamed(allowed);
    }

    static Set<String> allowedModelToolNames(SkillDefinition skill, Set<String> prefetchedTools, String message) {
        Set<String> allowed = new LinkedHashSet<>(skill.requiredTools());
        allowed.removeAll(prefetchedTools);
        String request = message == null ? "" : message;
        String artifactTool = requestedArtifactTool(skill, request);
        if (artifactTool != null) allowed.add(artifactTool);
        allowed.removeAll(prefetchedTools);
        return allowed;
    }

    static String requestedArtifactTool(SkillDefinition skill, String message) {
        String request = message == null ? "" : message;
        if (request.matches("(?is).*(excel|xlsx|csv|数据表|表格|清单).*")) return "exportWenchangData";
        if (!request.matches("(?is).*(word|docx|文档|报告|简报).*")) return null;
        if ("policy-brief".equals(skill.id())) return "createPolicyBrief";
        if ("study-tour-plan".equals(skill.id())) return "createStudyTourPackage";
        return "createWenchangWordReport";
    }

    private String enforceArtifactTruth(String value, boolean artifactToolCalled, List<ArtifactDescriptor> artifacts) {
        String answer = value == null ? "" : value;
        if (!artifactToolCalled || !artifacts.isEmpty()) return answer;
        String cleaned = java.util.Arrays.stream(answer.split("\\R"))
                .filter(line -> !line.matches(".*(已生成|生成成功|可直接下载|可下载打开|见下方文件).*"))
                .collect(java.util.stream.Collectors.joining("\n")).trim();
        return (cleaned.isBlank() ? "本次资料整理已完成，但文件成果未完成。" : cleaned)
                + "\n\n> 文件成果未完成：系统没有取得可下载的 Artifact，请稍后重试。";
    }

    static String appendArtifactLinks(String value, List<ArtifactDescriptor> artifacts) {
        String answer = value == null ? "" : value.trim();
        if (artifacts == null || artifacts.isEmpty()) return answer;
        StringBuilder links = new StringBuilder("\n\n## 已生成文件\n");
        for (ArtifactDescriptor artifact : artifacts) {
            String name = (artifact.displayName() == null || artifact.displayName().isBlank())
                    ? artifact.filename() : artifact.displayName();
            String label = (name == null ? "下载任务文件" : name).replace("[", "［").replace("]", "］");
            String url = artifact.downloadUrl();
            if (url == null || url.isBlank()) url = "/api/artifacts/" + artifact.id() + "/download";
            links.append("- [下载 ").append(label).append("](").append(url).append(")");
            if (artifact.sourceCount() > 0) links.append(" · ").append(artifact.sourceCount()).append(" 个来源");
            links.append('\n');
        }
        return answer + links;
    }

    private Map<String, Object> artifactEvent(ArtifactDescriptor artifact) {
        return mapper.convertValue(artifact, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
    }

    private String extractTown(String message) {
        for (String town : List.of("文城镇", "重兴镇", "蓬莱镇", "会文镇", "东路镇", "潭牛镇", "东阁镇",
                "文教镇", "东郊镇", "龙楼镇", "昌洒镇", "翁田镇", "抱罗镇", "冯坡镇", "锦山镇", "铺前镇", "公坡镇")) {
            if (message.contains(town) || message.contains(town.replace("镇", ""))) return town;
        }
        return "";
    }

    private String extractAgeGroup(String message) {
        if (message.contains("初一")) return "初一";
        if (message.contains("初二")) return "初二";
        if (message.contains("初三")) return "初三";
        if (message.matches(".*(初中|七年级|八年级|九年级).*")) return "初中";
        if (message.matches(".*(高中|高一|高二|高三).*")) return "高中";
        if (message.matches(".*(小学|小学生).*")) return "小学";
        if (message.matches(".*(大学|高校).*")) return "大学";
        return "全年龄";
    }

    private String dominantTheme(String message) {
        if (message.contains("航天")) return "航天";
        if (message.matches(".*(生态|海岸|红树林|湿地).*")) return "生态";
        if (message.matches(".*(历史|文化|非遗).*")) return "文化";
        return "研学";
    }

    private String resourceKeyword(String message) {
        if (message.matches(".*(高中|高一|高二|高三|初中|中学).*")) return "中学";
        if (message.matches(".*(小学|小学生).*")) return "小学";
        for (String keyword : List.of("医院", "学校", "图书馆", "文化馆", "博物馆", "体育", "政务服务",
                "交通", "应急", "公共安全", "科研", "科普")) if (message.contains(keyword)) return keyword;
        return "";
    }

    private String resourceCategory(String message) {
        if (message.matches(".*(医院|医疗|卫生).*")) return "医疗";
        if (message.matches(".*(学校|教育).*")) return "教育";
        if (message.matches(".*(图书馆|文化馆|博物馆|文化).*")) return "文化";
        if (message.matches(".*(体育|运动).*")) return "体育";
        if (message.matches(".*(政务|服务中心).*")) return "政务";
        if (message.matches(".*(公交|车站|交通).*")) return "交通";
        if (message.matches(".*(应急|消防|公共安全).*")) return "应急";
        return "";
    }

    private String safeMessage(String message) {
        if (message == null) return "未知错误";
        return message.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
    }

    private boolean isStreamingUnsupported(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof UnsupportedOperationException) return true;
            cursor = cursor.getCause();
        }
        return false;
    }

    private String preview(String text) {
        if (text == null) return "";
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "…";
    }

    private static final class RunStepState {
        private final String id;
        private final String label;
        private final String type;
        private final String toolName;
        private String status = "pending";
        private long latencyMs;
        private int sourceCount;
        private String toolSource;
        private String summary;
        private String errorType;
        private String errorMessage;
        private String inputPreview;
        private List<String> artifactIds = List.of();

        private RunStepState(String id, String label, String type, String toolName) {
            this.id = id; this.label = label; this.type = type; this.toolName = toolName;
        }

        private Map<String, Object> publicView() {
            return Map.of("id", id, "label", label, "type", type, "toolName", toolName, "status", status);
        }

        private Map<String, Object> eventData() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stepId", id);
            data.put("label", label);
            data.put("type", type);
            data.put("toolName", toolName);
            data.put("toolSource", toolSource == null ? "" : toolSource);
            data.put("status", status);
            data.put("latencyMs", latencyMs);
            data.put("sourceCount", sourceCount);
            data.put("summary", summary == null ? "" : summary);
            data.put("errorType", errorType == null ? "" : errorType);
            data.put("error", errorMessage == null ? "" : errorMessage);
            data.put("artifactIds", artifactIds);
            return data;
        }

        private AgentRunStep summary() {
            return new AgentRunStep(id, label, type, toolName, status, latencyMs, sourceCount,
                    toolSource, summary, errorType, errorMessage, inputPreview, artifactIds);
        }
    }

    private record ApprovalIntent(String actionType, String operation, String impactScope) { }
}
