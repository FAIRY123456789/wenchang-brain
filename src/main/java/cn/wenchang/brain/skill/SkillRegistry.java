package cn.wenchang.brain.skill;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** V1.5 Skill 的唯一注册表；requiredTools 与实际 Tool Registry 名称保持一致。 */
@Component
public class SkillRegistry {

    private final Map<String, SkillDefinition> skills;

    public SkillRegistry() {
        Map<String, SkillDefinition> items = new LinkedHashMap<>();
        register(items, skill("web-search", "/联网搜索", "联网搜索", "查询近期动态、开放状态与实时信息",
                List.of("webSearch"), List.of("current_topics"), WorkflowType.SINGLE_TOOL,
                "调用联网检索并标注检索时间；不要把旧资料表述为最新动态。",
                "研究", "实时信息与来源", "", "DIRECT"));
        register(items, skill("official-search", "/权威检索", "权威检索", "优先检索政府、官方和科研机构资料",
                List.of("officialSourceSearch"), List.of("policy_planning", "aerospace"), WorkflowType.SINGLE_TOOL,
                "只使用官方来源注册表允许的站点，输出机构、标题、日期与原始链接。",
                "研究", "官方来源清单", "", "DIRECT"));
        register(items, skill("evidence-check", "/证据核验", "证据核验", "核对信息依据、来源与资料一致性",
                List.of("knowledgeEvidence", "officialSourceSearch"), List.of(), WorkflowType.EVIDENCE_REVIEW,
                "输出结论、证据、来源和一致性判断；证据不足时明确标记未能核实。",
                "研究", "证据核验结果", "", "DIRECT"));
        register(items, skill("place-search", "/地点查询", "地点查询", "按地点、乡镇、主题与研学年龄查找",
                List.of("placeSearch"), List.of("tourism", "education_science", "ecology"), WorkflowType.SINGLE_TOOL,
                "返回真实地点字段与来源，不补写缺失坐标或开放信息。",
                "数据与地点", "地点清单", "", "DIRECT"));
        register(items, skill("study-tour-plan", "/研学方案", "研学方案", "按年龄、时长和主题规划路线并生成 Word",
                List.of("placeSearch", "knowledgeEvidence", "createStudyTourPackage"),
                List.of("tourism", "education_science", "aerospace", "ecology", "history", "study_tour"),
                WorkflowType.STUDY_TOUR_PLANNING,
                "先筛选地点，再按坐标做基本空间排序；列出时段、学习目标、活动、注意事项与来源并生成 Word。",
                "工作成果", "聊天回答 + Word 研学方案", "WORD", "USER_REQUEST_CONFIRMS"));
        register(items, skill("policy-search", "/政策检索", "政策检索", "查询政策原文、发布机构与时效状态",
                List.of("policySearch", "officialSourceSearch"), List.of("policy_planning"),
                WorkflowType.POLICY_RESEARCH,
                "优先输出发布机构、发布日期、文号、状态、核心内容与原始来源。",
                "研究", "政策清单与来源", "", "DIRECT"));
        register(items, skill("deep-research", "/深度研究", "深度研究", "多步骤检索、核验并综合来源",
                List.of("knowledgeEvidence", "officialSourceSearch", "webSearch", "collectOfficialMaterials",
                        "createWenchangWordReport"), List.of(),
                WorkflowType.DEEP_RESEARCH,
                "生成 4 至 6 个公开任务步骤，检索、采集和核验来源后生成专题 Word；禁止输出内部思维链。",
                "研究", "聊天回答 + Word 专题报告", "WORD", "USER_REQUEST_CONFIRMS"));
        register(items, skill("public-service", "/公共服务", "公共服务", "查询医院、学校、场馆、交通与政务资源",
                List.of("searchPublicServices"), List.of("public_services", "population_administration", "administrative_unit"), WorkflowType.MCP_PUBLIC_SERVICE,
                "调用文昌公共资源服务，以已核验结构化数据返回设施、地址、服务范围和来源。",
                "数据与地点", "公共资源清单", "", "DIRECT"));
        register(items, skill("latest-policy", "/最新政策", "最新政策", "按发布日期查找近期文昌与海南政策",
                List.of("policySearch", "officialSourceSearch", "webSearch"), List.of("policy_planning"),
                WorkflowType.POLICY_RESEARCH,
                "优先官方来源并按发布日期倒序；区分政策发布日期、网页更新时间与检索时间。",
                "研究", "最新政策清单", "", "DIRECT"));
        register(items, skill("policy-compare", "/政策对比", "政策对比", "对比政策范围、对象、措施与时间",
                List.of("policySearch", "officialSourceSearch", "knowledgeEvidence"), List.of("policy_planning"),
                WorkflowType.POLICY_RESEARCH,
                "以同一组字段对比政策，保留发布机构、发布日期与原始来源。",
                "研究", "政策对比表", "", "DIRECT"));
        register(items, skill("word-report", "/生成Word", "生成 Word", "把当前任务结果整理为可下载报告",
                List.of("createWenchangWordReport"), List.of(), WorkflowType.SINGLE_TOOL,
                "使用已取得的内容与来源生成结构完整的 Word，不补写不存在的事实。",
                "工作成果", "可下载 Word", "WORD", "USER_REQUEST_CONFIRMS"));
        register(items, skill("data-export", "/导出数据", "导出数据", "将地点、政策、公共服务或来源导出为表格",
                List.of("placeSearch", "exportWenchangData"), List.of(), WorkflowType.SINGLE_TOOL,
                "按用户要求选择数据集、字段和 CSV 或 Excel 格式，保留中文与来源 URL。",
                "工作成果", "CSV / Excel 数据文件", "DATA", "USER_REQUEST_CONFIRMS"));
        register(items, skill("policy-brief", "/政策简报", "政策简报", "检索、核验并生成 Word 政策简报",
                List.of("policySearch", "officialSourceSearch", "webSearch", "knowledgeEvidence",
                        "createPolicyBrief"), List.of("policy_planning"), WorkflowType.POLICY_RESEARCH,
                "生成包含摘要、政策列表、机构、日期、相关性、来源和更新时间的 Word 简报。",
                "工作成果", "聊天摘要 + Word 政策简报", "WORD", "USER_REQUEST_CONFIRMS"));
        this.skills = Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    public List<SkillDefinition> all() { return List.copyOf(skills.values()); }

    public Optional<SkillDefinition> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(skills.get(id.trim().toLowerCase()));
    }

    public SkillDefinition require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + id));
    }

    private SkillDefinition skill(String id, String command, String name, String description,
                                  List<String> tools, List<String> categories, WorkflowType type,
                                  String instruction, String group, String outputType,
                                  String artifactType, String approvalPolicy) {
        return new SkillDefinition(id, command, name, description, tools, categories, type, instruction,
                group, outputType, artifactType, approvalPolicy);
    }

    private void register(Map<String, SkillDefinition> items, SkillDefinition skill) {
        if (items.putIfAbsent(skill.id(), skill) != null) {
            throw new IllegalStateException("Duplicate skill: " + skill.id());
        }
    }
}
