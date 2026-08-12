package cn.wenchang.brain.controller;

import cn.wenchang.brain.agent.WenchangToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 只暴露工具目录，不执行工具、不泄露 MCP Server 凭据或内部配置。 */
@RestController
@RequestMapping("/api/agent")
public class AgentToolsController {

    private final WenchangToolRegistry registry;

    public AgentToolsController(WenchangToolRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/tools")
    public WenchangToolRegistry.ToolCatalog tools() { return registry.catalog(); }
}
