package cn.wenchang.brain.controller;

import cn.wenchang.brain.model.PersistedAgentRun;
import cn.wenchang.brain.service.AgentRunPersistenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/runs")
public class AgentRunController {

    private final AgentRunPersistenceService service;

    public AgentRunController(AgentRunPersistenceService service) { this.service = service; }

    @GetMapping
    public List<PersistedAgentRun> list(@RequestParam String conversationId) {
        return service.list(conversationId);
    }

    @GetMapping("/{id}")
    public PersistedAgentRun get(@PathVariable String id) { return service.require(id); }
}
