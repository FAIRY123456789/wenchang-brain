package cn.wenchang.brain.agent;

import cn.wenchang.brain.mcp.McpToolProviderAdapter;
import cn.wenchang.brain.tool.KnowledgeEvidenceTool;
import cn.wenchang.brain.tool.OfficialSourceSearchTool;
import cn.wenchang.brain.tool.PlaceSearchTool;
import cn.wenchang.brain.tool.PolicySearchTool;
import cn.wenchang.brain.tool.WebSearchTool;
import cn.wenchang.brain.tool.CollectOfficialMaterialsTool;
import cn.wenchang.brain.trace.ToolTraceCollector;
import cn.wenchang.brain.trace.TraceableToolCallback;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * 文昌 Agent 可见工具的唯一注册表。
 *
 * <p>Native @Tool 和 MCP Tool 最终都以 Spring AI ToolCallback 进入 ChatClient。工具名称与描述
 * 直接取自 ToolDefinition，避免在 Prompt、API 和执行层维护多份容易漂移的清单。</p>
 */
@Component
public class WenchangToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(WenchangToolRegistry.class);

    private final Map<String, ToolCallback> nativeTools;
    private final McpToolProviderAdapter mcpAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public WenchangToolRegistry(WebSearchTool webSearchTool,
                                OfficialSourceSearchTool officialSourceSearchTool,
                                KnowledgeEvidenceTool knowledgeEvidenceTool,
                                PlaceSearchTool placeSearchTool,
                                PolicySearchTool policySearchTool,
                                CollectOfficialMaterialsTool collectOfficialMaterialsTool,
                                McpToolProviderAdapter mcpAdapter) {
        this.mcpAdapter = mcpAdapter;
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        for (ToolCallback callback : ToolCallbacks.from(
                webSearchTool, officialSourceSearchTool, knowledgeEvidenceTool, placeSearchTool, policySearchTool)) {
            ToolCallback traced = new TraceableToolCallback(callback, "NATIVE");
            String name = traced.getToolDefinition().name();
            if (callbacks.putIfAbsent(name, traced) != null) {
                throw new IllegalStateException("Duplicate native tool name: " + name);
            }
        }
        ToolCallback collect = new TraceableToolCallback(collectOfficialMaterialsTool.callback(), "NATIVE");
        if (callbacks.putIfAbsent(collect.getToolDefinition().name(), collect) != null) {
            throw new IllegalStateException("Duplicate native tool name: " + collect.getToolDefinition().name());
        }
        this.nativeTools = Collections.unmodifiableMap(new LinkedHashMap<>(callbacks));
    }

    public List<String> nativeToolNames() { return List.copyOf(nativeTools.keySet()); }

    public List<String> mcpToolNames() {
        return mcpTools().stream().map(callback -> callback.getToolDefinition().name()).toList();
    }

    public List<ToolCallback> allCallbacks() { return callbacksExcluding(""); }

    public List<ToolCallback> callbacksExcluding(String excludedToolName) {
        return callbacksExcluding(excludedToolName == null || excludedToolName.isBlank()
                ? Set.of() : Set.of(excludedToolName));
    }

    public List<ToolCallback> callbacksExcluding(Set<String> excludedToolNames) {
        List<ToolCallback> combined = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (ToolCallback callback : nativeTools.values()) {
            String name = callback.getToolDefinition().name();
            if (!matchesToolName(excludedToolNames, name)) {
                names.add(name);
                combined.add(callback);
            }
        }
        for (ToolCallback callback : mcpTools()) {
            String name = callback.getToolDefinition().name();
            if (matchesToolName(excludedToolNames, name)) continue;
            if (!names.add(name)) {
                // MCP 默认前缀只保证 MCP 之间唯一；Registry 还必须保护 Native Tool 名称。
                log.warn("Ignoring MCP tool with conflicting name: {}", name);
                continue;
            }
            combined.add(callback);
        }
        return List.copyOf(combined);
    }

    static boolean matchesToolName(Set<String> requestedNames, String registeredName) {
        if (requestedNames == null || requestedNames.isEmpty() || registeredName == null) return false;
        return requestedNames.contains(registeredName) || requestedNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .anyMatch(name -> registeredName.endsWith("_" + name));
    }
    public List<ToolCallback> callbacksNamed(Set<String> allowedToolNames) {
        if (allowedToolNames == null || allowedToolNames.isEmpty()) return List.of();
        return callbacksExcluding(Set.of()).stream().filter(callback -> {
            String name = callback.getToolDefinition().name();
            return allowedToolNames.contains(name) || allowedToolNames.stream().anyMatch(name::endsWith);
        }).toList();
    }

    /** 确定性路由也通过 ToolCallback 执行，保证与模型自主调用拥有相同 Schema 和 Trace。 */
    public String invokeNative(String toolName, Map<String, Object> arguments, String traceId) {
        ToolCallback callback = nativeTools.get(toolName);
        if (callback == null) throw new IllegalArgumentException("Unknown native tool: " + toolName);
        try {
            String input = objectMapper.writeValueAsString(arguments);
            return callback.call(input, new ToolContext(Map.of(
                    ToolTraceCollector.TRACE_ID_CONTEXT_KEY, traceId)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid tool arguments", exception);
        }
    }

    /** Skill 编排可统一调用 Native 或 MCP Tool，并保留相同 Trace。 */
    public String invoke(String toolName, Map<String, Object> arguments, String traceId) {
        return invoke(toolName, arguments, traceId, Map.of());
    }

    public String invoke(String toolName, Map<String, Object> arguments, String traceId,
                         Map<String, Object> executionContext) {
        ToolCallback callback = nativeTools.get(toolName);
        if (callback == null) {
            callback = mcpTools().stream()
                    .filter(item -> item.getToolDefinition().name().equals(toolName)
                            || item.getToolDefinition().name().endsWith("_" + toolName))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));
        }
        try {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put(ToolTraceCollector.TRACE_ID_CONTEXT_KEY, traceId);
            if (executionContext != null) context.putAll(executionContext);
            return callback.call(objectMapper.writeValueAsString(arguments), new ToolContext(Map.copyOf(context)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid tool arguments", exception);
        }
    }

    public boolean hasTool(String toolName) {
        return nativeTools.containsKey(toolName) || mcpTools().stream().anyMatch(item ->
                item.getToolDefinition().name().equals(toolName)
                        || item.getToolDefinition().name().endsWith("_" + toolName));
    }

    public ToolCatalog catalog() {
        return new ToolCatalog(
                nativeTools.values().stream().map(callback -> describe(callback, "NATIVE")).toList(),
                mcpTools().stream().map(callback -> describe(callback, "MCP")).toList());
    }

    private ToolDescriptor describe(ToolCallback callback, String source) {
        return new ToolDescriptor(callback.getToolDefinition().name(),
                callback.getToolDefinition().description(), source);
    }

    private List<ToolCallback> mcpTools() {
        return Arrays.stream(mcpAdapter.discoverTools()).map(callback -> new TraceableToolCallback(callback, "MCP"))
                .map(ToolCallback.class::cast).toList();
    }

    public record ToolCatalog(List<ToolDescriptor> nativeTools, List<ToolDescriptor> mcpTools) { }

    /** 描述直接来自模型实际使用的 ToolDefinition，避免 API 与执行层出现两套文案。 */
    public record ToolDescriptor(String name, String description, String source) { }
}
