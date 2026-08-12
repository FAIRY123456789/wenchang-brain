package cn.wenchang.brain.rag;

import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.model.RetrievedChunk;
import cn.wenchang.brain.model.SourceRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 显式执行相似度检索以生成可读 Trace，同时提供同参数的 QuestionAnswerAdvisor 注入最终 Prompt。 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private final KnowledgeService knowledgeService;
    private final WenchangProperties properties;

    public RagService(KnowledgeService knowledgeService, WenchangProperties properties) {
        this.knowledgeService = knowledgeService;
        this.properties = properties;
    }

    public RagResult retrieve(String query) {
        return retrieve(query, List.of());
    }

    public RagResult retrieve(String query, List<String> preferredCategories) {
        return retrieve(query, preferredCategories, false);
    }

    public RagResult retrieve(String query, List<String> preferredCategories, boolean constrainToPreferred) {
        long started = System.nanoTime();
        SearchRequest request = searchRequest(query, preferredCategories, constrainToPreferred);
        List<Document> documents = knowledgeService.getVectorStore().similaritySearch(request);
        List<RetrievedChunk> chunks = new ArrayList<>();
        int rank = 1;
        for (Document document : documents) {
            chunks.add(new RetrievedChunk(rank++, document.getId(), sourceFile(document),
                    metadata(document, "section"), metadata(document, "category"),
                    metadata(document, "source_organization"), metadata(document, "source_url"),
                    metadata(document, "source_level"), metadata(document, "published_at"),
                    metadata(document, "retrieved_at"), document.getScore(), preview(document.getText())));
        }
        long latency = (System.nanoTime() - started) / 1_000_000;
        log.info("[RAG TRACE] query=\"{}\" retrieved={} latencyMs={}", query, chunks.size(), latency);
        chunks.forEach(chunk -> log.info("  rank={} score={} file={} section={}", chunk.rank(),
                chunk.score(), chunk.file(), chunk.section()));
        return new RagResult(documents, chunks, uniqueSources(chunks), latency);
    }

    public QuestionAnswerAdvisor advisorFor(String query) {
        return advisorFor(query, List.of());
    }

    public QuestionAnswerAdvisor advisorFor(String query, List<String> preferredCategories) {
        return advisorFor(query, preferredCategories, false);
    }

    public QuestionAnswerAdvisor advisorFor(String query, List<String> preferredCategories,
                                             boolean constrainToPreferred) {
        return QuestionAnswerAdvisor.builder(knowledgeService.getVectorStore())
                .searchRequest(searchRequest(query, preferredCategories, constrainToPreferred))
                .build();
    }

    private SearchRequest searchRequest(String query, List<String> preferredCategories,
                                        boolean constrainToPreferred) {
        SearchRequest.Builder request = SearchRequest.builder().query(query).topK(properties.getTopK())
                .similarityThreshold(properties.getSimilarityThreshold());
        Set<String> available = knowledgeService.getStatus().categories().keySet();
        List<String> availablePreferred = preferredCategories == null ? List.of()
                : preferredCategories.stream().filter(available::contains).distinct().toList();
        List<String> categories = constrainToPreferred && !availablePreferred.isEmpty()
                ? availablePreferred : detectCategories(query);
        if (categories.isEmpty()) {
            categories = availablePreferred;
        }
        if (!categories.isEmpty()) {
            var filter = new FilterExpressionBuilder().in("category", new ArrayList<Object>(categories)).build();
            request.filterExpression(filter);
        }
        return request.build();
    }

    /**
     * 本地特征哈希 Embedding 对跨领域共现词较敏感。仅当问题出现明确领域词时增加 metadata
     * 过滤；城市总览、开放问题仍保持全库向量检索。Trace 与 QuestionAnswerAdvisor 共用此请求。
     */
    private List<String> detectCategories(String query) {
        String text = query == null ? "" : query;
        Set<String> categories = new LinkedHashSet<>();
        addIfMatches(categories, text, "geography", "区位|位置|面积|地貌|水系|地理");
        addIfMatches(categories, text, "history", "历史|沿革|紫贝|古邑|传统聚落|古建筑");
        addIfMatches(categories, text, "population_administration", "人口|行政区划|统计口径|公共服务");
        addIfMatches(categories, text, "aerospace", "航天|火箭|卫星|发射场|商业航天");
        addIfMatches(categories, text, "ecology", "生态|红树林|湿地|保护区|生物多样性");
        addIfMatches(categories, text, "coast_ocean", "海岸|海湾|海洋|珊瑚礁|海草床|侵蚀");
        addIfMatches(categories, text, "disaster_climate", "台风|风暴潮|气候|灾害|暴雨");
        addIfMatches(categories, text, "economy_industry", "经济|产业|工业|园区");
        addIfMatches(categories, text, "agriculture", "农业|椰子|文昌鸡|渔业");
        addIfMatches(categories, text, "transportation", "交通|道路|高铁|出行");
        addIfMatches(categories, text, "education_science", "教育|学校|科研|科学");
        addIfMatches(categories, text, "culture", "文化|华侨|非遗|方言|民俗");
        addIfMatches(categories, text, "tourism", "旅游|景点|参访|观光");
        addIfMatches(categories, text, "food_folk_custom", "饮食|美食|文昌鸡|习俗");
        addIfMatches(categories, text, "historic_figures", "人物|宋氏|名人");
        addIfMatches(categories, text, "urban_development", "城市建设|城市更新|航天城");
        addIfMatches(categories, text, "policy_planning", "政策|规划|治理|政府文件");
        addIfMatches(categories, text, "study_tour", "研学|学习目标|科普路线");
        addIfMatches(categories, text, "current_topics", "近期|最新|当前|动态");
        return List.copyOf(categories);
    }

    private void addIfMatches(Set<String> categories, String text, String category, String expression) {
        if (text.matches(".*(" + expression + ").*")) categories.add(category);
    }

    private List<SourceRef> uniqueSources(List<RetrievedChunk> chunks) {
        Map<String, SourceRef> unique = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            String key = chunk.file() + "|" + chunk.section() + "|" + chunk.sourceUrl();
            unique.putIfAbsent(key, new SourceRef(chunk.file(), chunk.section(), chunk.category(),
                    chunk.sourceOrganization(), chunk.sourceUrl(), chunk.sourceLevel(),
                    chunk.publishedAt(), chunk.retrievedAt()));
        }
        return List.copyOf(unique.values());
    }

    private String metadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String sourceFile(Document document) {
        String relativePath = metadata(document, "relative_path");
        return relativePath.isBlank() ? metadata(document, "filename") : relativePath;
    }

    private String preview(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "…";
    }

    public record RagResult(List<Document> documents, List<RetrievedChunk> chunks,
                            List<SourceRef> sources, long latencyMs) { }
}
