package cn.wenchang.brain.search;

import cn.wenchang.brain.tool.OfficialSourceRegistry;
import cn.wenchang.brain.tool.WebSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deterministic post-retrieval ranking for Wenchang relevance, official provenance and recency. */
@Component
public class SearchResultRanker {

    private final OfficialSourceRegistry officialRegistry;

    @Autowired
    public SearchResultRanker(OfficialSourceRegistry officialRegistry) {
        this.officialRegistry = officialRegistry;
    }

    /** Test/compatibility constructor; built-in government-domain rules remain active. */
    public SearchResultRanker() { this.officialRegistry = null; }

    public List<WebSearchResult> rank(ChineseSearchQueryRewriter.SearchIntent intent,
                                      List<WebSearchResult> rawResults, int requestedLimit) {
        int limit = Math.max(1, Math.min(20, requestedLimit));
        Map<String, DomainTrust> trusted = trustedDomains();
        Map<String, Scored> unique = new LinkedHashMap<>();
        for (int index = 0; index < rawResults.size(); index++) {
            WebSearchResult result = rawResults.get(index);
            String canonical = canonicalUrl(result.url());
            if (canonical.isBlank()) continue;
            String host = host(result.url());
            if (isSearchRedirectWrapper(result.url(), host)) continue;
            String searchable = (result.title() + " " + result.snippet() + " " + host).toLowerCase(Locale.ROOT);
            if (intent.wenchang() && isGeographicMismatch(searchable)) continue;
            if (intent.wenchang() && !searchable.contains("文昌") && !host.contains("wenchang")) continue;
            double score = score(intent, result, host, searchable, trusted, index);
            Scored candidate = new Scored(result, score, index);
            unique.merge(canonical, candidate, (left, right) -> left.score() >= right.score() ? left : right);
        }
        return unique.values().stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed().thenComparingInt(Scored::originalIndex))
                .limit(limit).map(Scored::result).toList();
    }

    private double score(ChineseSearchQueryRewriter.SearchIntent intent, WebSearchResult result, String host,
                         String searchable, Map<String, DomainTrust> trusted, int index) {
        double score = Math.max(0, 12 - index);
        String title = result.title().toLowerCase(Locale.ROOT);
        String snippet = result.snippet().toLowerCase(Locale.ROOT);
        for (String keyword : intent.keywords()) {
            String normalized = keyword.toLowerCase(Locale.ROOT);
            if (title.contains(normalized)) score += 12;
            else if (snippet.contains(normalized)) score += 4;
        }
        if (title.contains("文昌")) score += 24;
        if (snippet.contains("文昌")) score += 8;
        if (searchable.contains("海南")) score += 6;

        DomainTrust trust = trustFor(host, trusted);
        score += trust.score();
        if (intent.official() && trust.score() > 0) score += 12;
        if (host.equals("hainan.gov.cn") || host.endsWith(".hainan.gov.cn")) score += 14;
        if (host.endsWith(".gov.cn") || host.equals("gov.cn")) score += 8;
        if (isLowAuthorityHost(host)) score -= 20;

        if (intent.recent()) score += recencyScore(result.publishedAt(), title + " " + snippet);
        return score;
    }

    private double recencyScore(String publishedAt, String text) {
        Instant date = parseDate(publishedAt);
        if (date != null) {
            long days = Math.max(0, ChronoUnit.DAYS.between(date, Instant.now()));
            if (days <= 7) return 30;
            if (days <= 30) return 24;
            if (days <= 180) return 16;
            if (days <= 365) return 9;
            return 0;
        }
        int year = LocalDate.now().getYear();
        if (text.contains(String.valueOf(year))) return 10;
        if (text.contains(String.valueOf(year - 1))) return 3;
        return 0;
    }

    private Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        try { return Instant.parse(value); } catch (DateTimeParseException ignored) { }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy/MM/dd"))) {
            try { return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value, formatter)
                    .atStartOfDay().toInstant(ZoneOffset.UTC); }
            catch (RuntimeException ignored) { }
        }
        java.util.regex.Matcher ago = java.util.regex.Pattern.compile("(\\d+)\\s*(day|days|天)\\s*(ago|前)?").matcher(value);
        if (ago.find()) return Instant.now().minus(Long.parseLong(ago.group(1)), ChronoUnit.DAYS);
        return null;
    }

    private Map<String, DomainTrust> trustedDomains() {
        Map<String, DomainTrust> result = new HashMap<>();
        if (officialRegistry != null) {
            for (OfficialSourceRegistry.OfficialSource source : officialRegistry.sources()) {
                double score = "P0".equalsIgnoreCase(source.level()) ? 42 : 28;
                result.put(source.domain().toLowerCase(Locale.ROOT), new DomainTrust(score, source.includeSubdomains()));
            }
        }
        result.putIfAbsent("hainan.gov.cn", new DomainTrust(42, true));
        result.putIfAbsent("gov.cn", new DomainTrust(32, true));
        return result;
    }

    private DomainTrust trustFor(String host, Map<String, DomainTrust> trusted) {
        DomainTrust exact = trusted.get(host);
        if (exact != null) return exact;
        for (Map.Entry<String, DomainTrust> entry : trusted.entrySet()) {
            if (entry.getValue().includeSubdomains() && host.endsWith("." + entry.getKey())) return entry.getValue();
        }
        return new DomainTrust(0, false);
    }

    private boolean isGeographicMismatch(String text) {
        return text.contains("台南") || text.contains("臺南") || text.contains("tainan")
                || text.contains("文昌区") || text.contains("文昌路") || text.contains("文昌阁");
    }

    private boolean isLowAuthorityHost(String host) {
        return host.contains("instagram.com") || host.contains("facebook.com") || host.contains("douyin.com")
                || host.contains("xiaohongshu.com") || host.contains("weibo.com");
    }

    private boolean isSearchRedirectWrapper(String rawUrl, String host) {
        try {
            String path = URI.create(rawUrl).getPath();
            path = path == null ? "" : path.toLowerCase(Locale.ROOT);
            return (host.equals("google.com") || host.endsWith(".google.com")) && path.startsWith("/goto")
                    || (host.equals("bing.com") || host.endsWith(".bing.com")) && path.contains("/ck/")
                    || (host.equals("sogou.com") || host.endsWith(".sogou.com")) && path.startsWith("/link");
        } catch (Exception ignored) { return true; }
    }

    private String canonicalUrl(String raw) {
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            return host + path;
        } catch (Exception ignored) { return ""; }
    }

    private String host(String raw) {
        try {
            String host = URI.create(raw).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (Exception ignored) { return ""; }
    }

    private record DomainTrust(double score, boolean includeSubdomains) { }
    private record Scored(WebSearchResult result, double score, int originalIndex) { }
}
