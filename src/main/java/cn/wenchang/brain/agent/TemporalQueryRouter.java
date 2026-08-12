package cn.wenchang.brain.agent;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * LLM 自主调用工具之外增加确定性护栏，解决“明显时效问题偶尔不搜索”的现场坏案例。
 * 规则只决定是否必须搜索，不替代模型对非时效问题的判断。
 */
@Component
public class TemporalQueryRouter {

    private static final Pattern TEMPORAL = Pattern.compile(
            "最近|近期|最新|今天|今日|目前|现在|本周|本月|刚刚|上一次|下一次|发射时间|"
                    + "天气|开放时间|开放状态|实时|政策更新|路况|交通状况");
    private static final Pattern LIVE_DOMAIN = Pattern.compile("航天|发射|天气|活动|开放|交通|政策");

    public boolean requiresWebSearch(String query) {
        return query != null && (TEMPORAL.matcher(query).find()
                || (LIVE_DOMAIN.matcher(query).find() && query.contains("什么时候")));
    }
}
