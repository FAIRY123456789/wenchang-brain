package cn.wenchang.brain.controller;

import cn.wenchang.brain.model.PolicyRefreshReport;
import cn.wenchang.brain.service.PolicyRefreshService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {

    private final PolicyRefreshService refreshService;

    public AdminKnowledgeController(PolicyRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @PostMapping("/refresh-policies")
    public PolicyRefreshReport refreshPolicies() throws IOException { return refreshService.refresh(); }
}
