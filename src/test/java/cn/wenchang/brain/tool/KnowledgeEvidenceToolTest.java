package cn.wenchang.brain.tool;

import cn.wenchang.brain.model.RetrievedChunk;
import cn.wenchang.brain.model.SourceRef;
import cn.wenchang.brain.rag.RagService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeEvidenceToolTest {

    @Test
    void returnsRetrievedEvidenceWithSourceMetadataWithoutGuessing() throws Exception {
        RagService ragService = mock(RagService.class);
        RetrievedChunk chunk = new RetrievedChunk(1, "doc-1", "04_aerospace/source.md", "发射场",
                "航天", "国家航天局", "https://www.cnsa.gov.cn/example", "P0",
                "2026-01-01", "2026-08-11", 0.91, "文昌航天发射场相关证据片段");
        when(ragService.retrieve("文昌航天发展的依据是什么？"))
                .thenReturn(new RagService.RagResult(List.of(), List.of(chunk), List.<SourceRef>of(), 2));
        KnowledgeEvidenceTool tool = new KnowledgeEvidenceTool(ragService);

        JsonNode output = new ObjectMapper().readTree(tool.knowledgeEvidence("文昌航天发展的依据是什么？"));

        JsonNode evidence = output.path("evidence").get(0);
        assertThat(evidence.path("sourceOrganization").asText()).isEqualTo("国家航天局");
        assertThat(evidence.path("sourceUrl").asText()).isEqualTo("https://www.cnsa.gov.cn/example");
        assertThat(evidence.path("sourceLevel").asText()).isEqualTo("P0");
        assertThat(evidence.path("score").asDouble()).isEqualTo(0.91);
    }
}
