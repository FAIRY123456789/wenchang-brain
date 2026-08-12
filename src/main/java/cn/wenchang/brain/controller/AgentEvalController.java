package cn.wenchang.brain.controller;

import cn.wenchang.brain.eval.AgentExperienceEvalReport;
import cn.wenchang.brain.eval.AgentExperienceEvalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin")
public class AgentEvalController {

    private final AgentExperienceEvalService service;

    public AgentEvalController(AgentExperienceEvalService service) { this.service = service; }

    @PostMapping("/agent-eval")
    public AgentExperienceEvalReport run() throws IOException { return service.run(); }
}
