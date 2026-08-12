package cn.wenchang.brain.controller;

import cn.wenchang.brain.diagnostics.AgentDiagnosticReport;
import cn.wenchang.brain.diagnostics.AgentDiagnosticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/diagnostics")
public class AgentDiagnosticsController {

    private final AgentDiagnosticsService diagnostics;

    public AgentDiagnosticsController(AgentDiagnosticsService diagnostics) {
        this.diagnostics = diagnostics;
    }

    @GetMapping("/agent")
    public AgentDiagnosticReport agent() { return diagnostics.run(); }
}
