package cn.wenchang.brain.tool;

import cn.wenchang.brain.model.RetrievedChunk;
import cn.wenchang.brain.rag.RagService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 显式证据查询工具。默认问答仍由 QuestionAnswerAdvisor 自动执行 RAG；只有用户追问依据、
 * 来源或资料时，该 Tool 才把 VectorStore 中的 Top-K Chunk 及其来源元数据作为可见证据返回。
 */
@Component
public class KnowledgeEvidenceTool {

    private final RagService ragService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public KnowledgeEvidenceTool(RagService ragService) {
        this.ragService = ragService;
    }

    @Tool(name = "knowledgeEvidence", description = """
            查询文昌本地知识库中的明确证据和来源。用户询问“依据是什么”“来源在哪里”“有哪些资料”
            “这个信息来自哪里”或需要补充可核验资料时使用。返回相关内容片段、文件、章节、机构、URL、
            发布日期、检索日期和相似度；不得编造缺失的来源字段。
            """)
    public String knowledgeEvidence(
            @ToolParam(description = "需要在文昌知识库中查找证据的完整问题") String query) {
        String normalized = query == null ? "" : query.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) return json(new EvidenceResponse(List.of(), "查询不能为空"));
        RagService.RagResult result = ragService.retrieve(normalized);
        List<EvidenceItem> items = new ArrayList<>();
        for (RetrievedChunk chunk : result.chunks()) {
            items.add(new EvidenceItem(chunk.preview(), chunk.file(), chunk.section(),
                    chunk.category(), chunk.sourceOrganization(), chunk.sourceUrl(), chunk.sourceLevel(),
                    chunk.publishedAt(), chunk.retrievedAt(), chunk.score()));
        }
        String message = items.isEmpty() ? "知识库中没有达到相似度阈值的证据。" : "";
        return json(new EvidenceResponse(List.copyOf(items), message));
    }

    private String json(EvidenceResponse response) {
        try { return objectMapper.writeValueAsString(response); }
        catch (JsonProcessingException exception) { return "{\"evidence\":[],\"message\":\"结果序列化失败\"}"; }
    }

    public record EvidenceItem(String contentSnippet, String sourceFile, String section, String category,
                               String sourceOrganization, String sourceUrl, String sourceLevel,
                               String publishedAt, String retrievedAt, Double score) { }
    public record EvidenceResponse(List<EvidenceItem> evidence, String message) { }
}
