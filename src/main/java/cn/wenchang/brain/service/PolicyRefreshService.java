package cn.wenchang.brain.service;

import cn.wenchang.brain.model.PolicyRefreshReport;
import cn.wenchang.brain.rag.KnowledgeService;
import cn.wenchang.brain.tool.OfficialSourceSearchTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理员触发的政策发现流程。新搜索结果先进入候选清单，不把搜索摘要直接写入正式知识；
 * 已审核的 policy JSON / Markdown / Sources Index 仍由同一次接口触发重新索引。
 */
@Service
public class PolicyRefreshService {

    private static final List<String> TOPICS = List.of(
            "文昌 商业航天 国际航天城 最新政策",
            "文昌 生态环境 海洋经济 最新政策",
            "文昌 教育 公共服务 最新政策",
            "文昌 乡村振兴 农业 基础设施 最新政策"
    );

    private final OfficialSourceSearchTool officialSearch;
    private final KnowledgeService knowledgeService;
    private final Path policiesFile;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public PolicyRefreshService(OfficialSourceSearchTool officialSearch, KnowledgeService knowledgeService,
                                @Value("${wenchang.policies-file:data/wenchang-policies.json}") String policiesFile) {
        this.officialSearch = officialSearch;
        this.knowledgeService = knowledgeService;
        this.policiesFile = Path.of(policiesFile).toAbsolutePath().normalize();
    }

    public PolicyRefreshReport refresh() throws IOException {
        Set<String> knownUrls = knownPolicyUrls();
        Set<String> candidates = new LinkedHashSet<>();
        for (String topic : TOPICS) collectUrls(officialSearch.officialSourceSearch(topic), candidates);
        List<String> newCandidates = candidates.stream().filter(url -> !knownUrls.contains(url)).toList();
        return new PolicyRefreshReport(Instant.now(), TOPICS.size(), candidates.size(), newCandidates.size(),
                newCandidates, "候选内容必须打开官方原文并人工核验后才能进入 policy JSON、Markdown 与 Sources Index。",
                knowledgeService.reindex());
    }

    private Set<String> knownPolicyUrls() throws IOException {
        if (!Files.isRegularFile(policiesFile)) return Set.of();
        JsonNode root = mapper.readTree(policiesFile.toFile());
        JsonNode policies = root.isArray() ? root : root.path("policies");
        Set<String> result = new LinkedHashSet<>();
        if (policies.isArray()) policies.forEach(item -> {
            String url = item.path("sourceUrl").asText("").trim();
            if (!url.isBlank()) result.add(url);
        });
        return result;
    }

    private void collectUrls(String json, Set<String> result) {
        try {
            JsonNode root = mapper.readTree(json);
            List<JsonNode> nodes = new ArrayList<>();
            if (root.isArray()) root.forEach(nodes::add);
            for (String key : List.of("results", "sources", "items")) {
                JsonNode value = root.path(key);
                if (value.isArray()) value.forEach(nodes::add);
            }
            for (JsonNode node : nodes) {
                String url = node.path("url").asText("").trim();
                if (!url.isBlank()) result.add(url);
            }
        } catch (Exception ignored) { }
    }
}
