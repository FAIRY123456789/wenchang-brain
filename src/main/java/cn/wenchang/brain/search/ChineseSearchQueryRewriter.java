package cn.wenchang.brain.search;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts conversational Chinese instructions into a compact, geographically explicit search query. */
@Component
public class ChineseSearchQueryRewriter {

    private static final Pattern QUOTED = Pattern.compile("[“\"]([^”\"]{2,160})[”\"]");
    private static final Pattern SITE = Pattern.compile("(?i)site:([a-z0-9.-]+)");
    private static final Pattern RECENT = Pattern.compile("最新|近期|最近|今天|今日|当前|本周|本月|动态|新闻|公告|通知");
    private static final Pattern NEWS = Pattern.compile("新闻|动态|要闻|资讯|发布|公告");
    private static final Pattern OFFICIAL = Pattern.compile("官方|政府|政策|公告|通知|公示|部门|原文");
    private static final List<String> SEMANTIC_TERMS = List.of(
            "文昌", "海南", "人民政府", "政府", "新闻", "动态", "政策", "公告", "通知", "公示",
            "航天", "发射", "国际航天城", "旅游", "交通", "天气", "生态", "教育", "农业", "民生");

    public SearchIntent rewrite(String rawQuery) {
        String original = normalize(rawQuery);
        if (original.isBlank()) throw new SearchProviderException("INVALID_QUERY", "搜索词不能为空");

        List<String> sites = new ArrayList<>(extractSites(original));
        String core = quotedCore(original);
        core = SITE.matcher(core).replaceAll(" ");
        core = core.replaceAll("(?i)只(?:需|要)?调用一次(?:联网)?搜索(?:工具)?", " ")
                .replaceAll("(?i)(?:请|帮我|麻烦|能否|可以)?(?:使用|进行|调用)?(?:一次)?(?:联网|网页|网络)?搜索", " ")
                .replaceAll("(?i)基于真实搜索结果.*$", " ")
                .replaceAll("(?i)(?:并)?(?:简短)?(?:列出|返回|展示).*?(?:前|最多)?[一二三四五六七八九十0-9]+条", " ")
                .replaceAll("(?i)标题和链接|不要生成长篇回答|不要长篇回答|简短回答", " ")
                .replaceAll("[，。！？、；：‘’“”()（）【】\\[\\]]", " ")
                .replaceAll("\\s+", " ").trim();

        boolean wenchang = core.contains("文昌") || original.contains("文昌");
        boolean recent = RECENT.matcher(core + " " + original).find();
        boolean news = NEWS.matcher(core + " " + original).find();
        boolean official = OFFICIAL.matcher(core + " " + original).find();
        if (sites.isEmpty()) sites.addAll(inferOfficialDomains(core));

        List<String> parts = new ArrayList<>();
        if (wenchang && !core.contains("海南")) parts.add("海南");
        if (!core.isBlank()) parts.add(core);
        if (news && !core.contains("新闻")) parts.add("新闻");
        if (official && !containsAny(core, "政府", "官方", "公告", "通知", "公示")) parts.add("官方");
        if (recent && !core.matches(".*20\\d{2}.*")) parts.add(String.valueOf(LocalDate.now().getYear()));
        sites.forEach(site -> parts.add("site:" + site));

        String rewritten = deduplicateWords(String.join(" ", parts));
        return new SearchIntent(original, rewritten, keywords(core, wenchang), recent, news, official, wenchang,
                List.copyOf(sites));
    }

    private String quotedCore(String input) {
        Matcher matcher = QUOTED.matcher(input);
        return matcher.find() ? matcher.group(1).trim() : input;
    }

    private List<String> extractSites(String input) {
        Set<String> sites = new LinkedHashSet<>();
        Matcher matcher = SITE.matcher(input);
        while (matcher.find()) sites.add(matcher.group(1).toLowerCase(Locale.ROOT));
        return List.copyOf(sites);
    }

    private List<String> inferOfficialDomains(String core) {
        if (core.matches(".*(文昌市人民政府|文昌市政府|文昌政府).*")) return List.of("hainan.gov.cn");
        if (core.matches(".*(海南省人民政府|海南省政府).*")) return List.of("hainan.gov.cn");
        if (core.contains("文昌国际航天城管理局")) return List.of("wchtc.net");
        if (core.contains("国家航天局")) return List.of("cnsa.gov.cn");
        if (core.contains("中国载人航天")) return List.of("cmse.gov.cn");
        return List.of();
    }

    private List<String> keywords(String core, boolean wenchang) {
        Set<String> terms = new LinkedHashSet<>();
        if (wenchang) terms.add("文昌");
        for (String term : SEMANTIC_TERMS) if (core.contains(term)) terms.add(term);
        Matcher latinOrNumber = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{2,}|20\\d{2}").matcher(core);
        while (latinOrNumber.find()) terms.add(latinOrNumber.group().toLowerCase(Locale.ROOT));
        return List.copyOf(terms);
    }

    private String deduplicateWords(String value) {
        Set<String> words = new LinkedHashSet<>();
        for (String word : value.replaceAll("\\s+", " ").trim().split(" ")) {
            if (!word.isBlank()) words.add(word);
        }
        return String.join(" ", words);
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public record SearchIntent(String originalQuery, String rewrittenQuery, List<String> keywords,
                               boolean recent, boolean news, boolean official, boolean wenchang,
                               List<String> requestedDomains) { }
}
