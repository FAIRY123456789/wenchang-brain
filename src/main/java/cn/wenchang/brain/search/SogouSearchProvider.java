package cn.wenchang.brain.search;

import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.tool.WebSearchResult;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 搜狗 HTML Provider。反爬跳转会被分类为 ANTI_BOT，而不是伪装成 READY。 */
public final class SogouSearchProvider implements SearchProvider {

    private static final Pattern RESULT_LINK = Pattern.compile(
            "(?is)<a([^>]*)class=\\\"[^\\\"]*result__a[^\\\"]*\\\"([^>]*)>(.*?)</a>");
    private static final Pattern HREF = Pattern.compile("(?is)href=\\\"([^\\\"]+)\\\"");
    private static final Pattern SNIPPET = Pattern.compile(
            "(?is)<(?:a|div)[^>]*class=\\\"[^\\\"]*result__snippet[^\\\"]*\\\"[^>]*>(.*?)</(?:a|div)>");
    private static final Pattern SOGOU_LINK = Pattern.compile(
            "(?is)<h3[^>]*class=\\\"[^\\\"]*vr-title[^\\\"]*\\\"[^>]*>.*?<a([^>]*)>(.*?)</a>.*?</h3>");
    private static final Pattern SOGOU_SNIPPET = Pattern.compile(
            "(?is)<div[^>]*class=\\\"[^\\\"]*fz-mid[^\\\"]*\\\"[^>]*>(.*?)</div>");

    private final WenchangProperties properties;
    private volatile SearchProviderHealth health = new SearchProviderHealth(
            "sogou", "UNKNOWN", 0, null, "尚未执行健康检查", "NOT_CHECKED");

    public SogouSearchProvider(WenchangProperties properties) {
        this.properties = properties;
    }

    @Override public String id() { return "sogou"; }

    @Override
    public List<WebSearchResult> search(String query, int requestedLimit) {
        long started = System.nanoTime();
        String normalized = query == null ? "" : query.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) throw fail(started, "INVALID_QUERY", "搜索词不能为空", null);
        if (normalized.length() > 500) normalized = normalized.substring(0, 500);
        int limit = Math.max(1, Math.min(10, requestedLimit));
        HttpURLConnection connection = null;
        try {
            String encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8);
            String endpoint = properties.getWebSearch().getEndpoint();
            String separator = endpoint.contains("?") ? "&" : "?";
            URI uri = URI.create(endpoint + separator + "query=" + encoded);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            int timeoutMs = properties.getWebSearch().getTimeoutSeconds() * 1000;
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(false);
            configureHeaders(connection);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            if (status >= 300 && status < 400) {
                String type = isAntiBot(location) ? "ANTI_BOT" : "HTTP_REDIRECT";
                throw new SearchProviderException(type, "HTTP " + status + " redirect"
                        + (isAntiBot(location) ? " to anti-bot challenge" : ""));
            }
            if (status < 200 || status >= 300) {
                throw new SearchProviderException("HTTP_" + status, "HTTP " + status);
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] body = input.readNBytes(2_000_000);
                String html = new String(body, StandardCharsets.UTF_8);
                if (isAntiBot(html)) throw new SearchProviderException("ANTI_BOT", "anti-bot challenge page");
                List<WebSearchResult> results = parseHtml(html, uri, limit);
                success(started);
                return results;
            }
        } catch (SearchProviderException exception) {
            throw fail(started, exception.errorType(), exception.getMessage(), exception);
        } catch (java.net.SocketTimeoutException exception) {
            throw fail(started, "TIMEOUT", "搜索请求超时", exception);
        } catch (Exception exception) {
            throw fail(started, "NETWORK_ERROR", safe(exception.getMessage()), exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Override
    public SearchProviderHealth healthCheck() {
        try { search("文昌市人民政府", 1); }
        catch (SearchProviderException ignored) { }
        return currentHealth();
    }

    @Override public SearchProviderHealth currentHealth() { return health; }

    @Override
    public String resolveExternalUrl(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) return "";
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(rawLink);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.endsWith("sogou.com") && !host.endsWith("duckduckgo.com")) return rawLink;
            connection = (HttpURLConnection) uri.toURL().openConnection();
            int timeoutMs = Math.min(8_000, properties.getWebSearch().getTimeoutSeconds() * 1000);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(true);
            configureHeaders(connection);
            connection.setRequestMethod("GET");
            connection.getResponseCode();
            URL resolved = connection.getURL();
            return resolved == null ? rawLink : resolved.toString();
        } catch (Exception ignored) {
            return rawLink;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public List<WebSearchResult> parseHtml(String html, URI baseUri, int limit) {
        if (SOGOU_LINK.matcher(html).find()) return parseSogouHtml(html, baseUri, limit);
        List<String> snippets = new ArrayList<>();
        Matcher snippetMatcher = SNIPPET.matcher(html);
        while (snippetMatcher.find() && snippets.size() < limit) snippets.add(cleanHtml(snippetMatcher.group(1)));
        List<WebSearchResult> results = new ArrayList<>();
        Matcher linkMatcher = RESULT_LINK.matcher(html);
        int index = 0;
        while (linkMatcher.find() && index < limit) {
            String attributes = linkMatcher.group(1) + linkMatcher.group(2);
            Matcher hrefMatcher = HREF.matcher(attributes);
            String link = hrefMatcher.find() ? absoluteLink(decodeDuckLink(hrefMatcher.group(1)), baseUri) : "";
            String title = cleanHtml(linkMatcher.group(3));
            String snippet = index < snippets.size() ? snippets.get(index) : "";
            if (!title.isBlank() && !link.isBlank()) results.add(new WebSearchResult(title, link, snippet));
            index++;
        }
        return List.copyOf(results);
    }

    private List<WebSearchResult> parseSogouHtml(String html, URI baseUri, int limit) {
        List<String> snippets = new ArrayList<>();
        Matcher matcher = SOGOU_SNIPPET.matcher(html);
        while (matcher.find() && snippets.size() < limit) snippets.add(cleanHtml(matcher.group(1)));
        List<WebSearchResult> results = new ArrayList<>();
        Matcher links = SOGOU_LINK.matcher(html);
        int index = 0;
        while (links.find() && index < limit) {
            Matcher href = HREF.matcher(links.group(1));
            String link = href.find() ? absoluteLink(htmlDecode(href.group(1)), baseUri) : "";
            String title = cleanHtml(links.group(2));
            String snippet = index < snippets.size() ? snippets.get(index) : "";
            if (!title.isBlank() && !link.isBlank()) results.add(new WebSearchResult(title, link, snippet));
            index++;
        }
        return List.copyOf(results);
    }

    private SearchProviderException fail(long started, String type, String message, Throwable cause) {
        long latency = (System.nanoTime() - started) / 1_000_000;
        health = new SearchProviderHealth(id(), "UNAVAILABLE", latency, health.lastSuccess(),
                safe(message), type);
        return cause == null ? new SearchProviderException(type, safe(message))
                : new SearchProviderException(type, safe(message), cause);
    }

    private void success(long started) {
        health = new SearchProviderHealth(id(), "AVAILABLE",
                (System.nanoTime() - started) / 1_000_000, Instant.now(), "", "");
    }

    private boolean isAntiBot(String value) {
        return value != null && (value.toLowerCase(Locale.ROOT).contains("antispider")
                || value.toLowerCase(Locale.ROOT).contains("anti-bot"));
    }

    private String decodeDuckLink(String raw) {
        String decoded = htmlDecode(raw);
        int marker = decoded.indexOf("uddg=");
        if (marker < 0) return decoded.startsWith("//") ? "https:" + decoded : decoded;
        String value = decoded.substring(marker + 5);
        int amp = value.indexOf('&');
        if (amp >= 0) value = value.substring(0, amp);
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String cleanHtml(String value) {
        return htmlDecode(value.replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim());
    }

    private String htmlDecode(String value) {
        return value.replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#x27;", "'").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
    }

    private String absoluteLink(String value, URI baseUri) {
        if (value == null || value.isBlank()) return "";
        if (value.startsWith("//")) return "https:" + value;
        try { return baseUri.resolve(value).toString(); }
        catch (Exception ignored) { return value; }
    }

    private void configureHeaders(HttpURLConnection connection) {
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
    }
}
