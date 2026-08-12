package cn.wenchang.brain.controller;

import cn.wenchang.brain.agent.AgentProfile;
import cn.wenchang.brain.agent.AgentProfileRegistry;
import cn.wenchang.brain.skill.SkillDefinition;
import cn.wenchang.brain.skill.SkillRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 前端 @Agent 和 /Skill 面板的数据源。 */
@RestController
@RequestMapping("/api")
public class AgentExperienceController {

    private final AgentProfileRegistry agentProfiles;
    private final SkillRegistry skills;

    public AgentExperienceController(AgentProfileRegistry agentProfiles, SkillRegistry skills) {
        this.agentProfiles = agentProfiles;
        this.skills = skills;
    }

    @GetMapping("/agents")
    public List<AgentProfile> agents() { return agentProfiles.all(); }

    @GetMapping("/agents/{id}")
    public AgentProfile agent(@PathVariable String id) { return agentProfiles.require(id); }

    @GetMapping("/skills")
    public List<SkillDefinition> skills() { return skills.all(); }

    @GetMapping("/skills/{id}")
    public SkillDefinition skill(@PathVariable String id) { return skills.require(id); }
}
