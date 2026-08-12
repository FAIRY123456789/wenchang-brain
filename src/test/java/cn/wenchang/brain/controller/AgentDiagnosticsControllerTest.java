package cn.wenchang.brain.controller;

import cn.wenchang.brain.diagnostics.AgentDiagnosticReport;
import cn.wenchang.brain.diagnostics.AgentDiagnosticsService;
import cn.wenchang.brain.search.SearchProviderHealth;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDiagnosticsControllerTest {

    @Test
    void returnsUnifiedStatusMatrixFromService() {
        AgentDiagnosticsService service = mock(AgentDiagnosticsService.class);
        AgentDiagnosticReport.Check ok = AgentDiagnosticReport.Check.available(3, "ok");
        AgentDiagnosticReport report = new AgentDiagnosticReport(Instant.now(),
                new AgentDiagnosticReport.ModelDiagnostic("deepseek", "deepseek-chat", true,
                        true, 10, true, "trace", "AVAILABLE", ""),
                new AgentDiagnosticReport.ToolDiagnostic(List.of("webSearch"), Map.of("webSearch", ok)),
                new AgentDiagnosticReport.McpDiagnostic(List.of(), false, List.of()),
                new AgentDiagnosticReport.SearchDiagnostic(new SearchProviderHealth(
                        "mock", "AVAILABLE", 3, Instant.now(), "", ""), ok),
                new AgentDiagnosticReport.RagDiagnostic(true, 50, 136),
                new AgentDiagnosticReport.ArtifactDiagnostic(ok, ok));
        when(service.run()).thenReturn(report);

        assertThat(new AgentDiagnosticsController(service).agent()).isSameAs(report);
    }
}
