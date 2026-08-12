package cn.wenchang.brain.agent;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 为必须稳定触发的能力提供确定性护栏；普通问题仍由模型根据 Tool 描述自主选择。
 * 一次请求最多预取一个工具，预取后的同名 Tool 会从本轮模型可见列表移除，避免重复执行。
 */
@Component
public class CapabilityRouter {

    private static final Pattern EVIDENCE = Pattern.compile(
            "依据(是|有|来自)?什么|依据哪些|来源(是|在|于)?哪|信息来自哪|有哪些资料|相关资料|参考资料|证据");
    private static final Pattern OFFICIAL = Pattern.compile(
            "官方(资料|来源|依据|数据|文件|公告|政策|网站)|政府(资料|数据|项目|文件|公告)|"
                    + "政策|行政数据|统计数据|生态保护|教育数据|航天任务");

    private final TemporalQueryRouter temporalQueryRouter;

    public CapabilityRouter(TemporalQueryRouter temporalQueryRouter) {
        this.temporalQueryRouter = temporalQueryRouter;
    }

    public RoutingDecision route(String query) {
        if (query == null || query.isBlank()) return RoutingDecision.none();
        if (OFFICIAL.matcher(query).find()) return new RoutingDecision("officialSourceSearch", "正在查询权威来源");
        if (EVIDENCE.matcher(query).find()) return new RoutingDecision("knowledgeEvidence", "正在查询知识依据");
        if (temporalQueryRouter.requiresWebSearch(query)) return new RoutingDecision("webSearch", "正在联网搜索");
        return RoutingDecision.none();
    }

    public record RoutingDecision(String toolName, String progressMessage) {
        public static RoutingDecision none() { return new RoutingDecision("", ""); }
        public boolean required() { return toolName != null && !toolName.isBlank(); }
    }
}
