package cn.wenchang.brain.controller;

import cn.wenchang.brain.model.AgentApproval;
import cn.wenchang.brain.model.AgentApprovalRequest;
import cn.wenchang.brain.service.AgentApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/approvals")
public class AgentApprovalController {

    private final AgentApprovalService service;

    public AgentApprovalController(AgentApprovalService service) { this.service = service; }

    @PostMapping("/preview")
    public AgentApproval preview(@Valid @RequestBody AgentApprovalRequest request) {
        return service.preview(request);
    }

    @PostMapping("/{id}/confirm")
    public AgentApproval confirm(@PathVariable String id) { return service.confirm(id); }

    @PostMapping("/{id}/cancel")
    public AgentApproval cancel(@PathVariable String id) { return service.cancel(id); }

    @GetMapping
    public List<AgentApproval> list(@RequestParam String conversationId) {
        return service.list(conversationId);
    }
}
