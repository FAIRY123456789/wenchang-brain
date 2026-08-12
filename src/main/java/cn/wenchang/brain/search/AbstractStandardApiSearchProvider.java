package cn.wenchang.brain.search;

import cn.wenchang.brain.tool.WebSearchResult;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Shared production behavior for authenticated JSON search APIs. */
abstract class AbstractStandardApiSearchProvider implements SearchProvider {

    private static final Duration HEALTH_PROBE_TTL = Duration.ofSeconds(60);
    private final String providerId;
    private final int timeoutSeconds;
    private volatile SearchProviderHealth health;
    private volatile Instant lastProbe;

    AbstractStandardApiSearchProvider(String providerId, int timeoutSeconds) {
        this.providerId = providerId;
        this.timeoutSeconds = Math.max(2, timeoutSeconds);
        this.health = new SearchProviderHealth(providerId, "UNKNOWN", 0, null,
                "尚未执行健康检查", "NOT_CHECKED");
    }

    @Override public final String id() { return providerId; }

    @Override
    public final List<WebSearchResult> search(String query, int requestedLimit) {
        long started = System.nanoTime();
        String normalized = normalizeQuery(query);
        int limit = Math.max(1, Math.min(20, requestedLimit));
        try {
            List<WebSearchResult> results = executeSearch(normalized, limit);
            long latency = elapsed(started);
            health = new SearchProviderHealth(id(), "AVAILABLE", latency, Instant.now(), "", "");
            lastProbe = Instant.now();
            return List.copyOf(results);
        } catch (SearchProviderException exception) {
            recordFailure(started, exception.errorType(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(started, "SEARCH_RESPONSE_INVALID", safe(exception.getMessage()));
            throw new SearchProviderException("SEARCH_RESPONSE_INVALID", safe(exception.getMessage()), exception);
        }
    }

    protected abstract List<WebSearchResult> executeSearch(String query, int limit);

    @Override
    public final SearchProviderHealth healthCheck() {
        Instant checked = lastProbe;
        if (checked != null && Duration.between(checked, Instant.now()).compareTo(HEALTH_PROBE_TTL) < 0) {
            return health;
        }
        try { search("文昌", 1); }
        catch (SearchProviderException ignored) { }
        return health;
    }

    @Override public final SearchProviderHealth currentHealth() { return health; }

    protected final String executeJson(String method, String endpoint, Map<String, String> headers, String body) {
        SearchProviderException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) java.net.URI.create(endpoint).toURL().openConnection();
                int timeoutMs = timeoutSeconds * 1000;
                connection.setConnectTimeout(timeoutMs);
                connection.setReadTimeout(timeoutMs);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod(method);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "WenchangBrain/1.5");
                headers.forEach(connection::setRequestProperty);
                if (body != null) {
                    connection.setDoOutput(true);
                    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(payload.length);
                    try (var output = connection.getOutputStream()) { output.write(payload); }
                }
                int status = connection.getResponseCode();
                String response = readResponse(connection, status);
                if (status >= 200 && status < 300) return response;
                SearchProviderException failure = httpFailure(status, response);
                if (!retriable(status) || attempt == 1) throw failure;
                last = failure;
                boundedBackoff(attempt);
            } catch (SearchProviderException exception) {
                throw exception;
            } catch (java.net.SocketTimeoutException exception) {
                last = new SearchProviderException("TIMEOUT", "标准搜索 API 请求超时", exception);
                if (attempt == 1) throw last;
                boundedBackoff(attempt);
            } catch (Exception exception) {
                last = new SearchProviderException("NETWORK_ERROR", safe(exception.getMessage()), exception);
                if (attempt == 1) throw last;
                boundedBackoff(attempt);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw last == null ? new SearchProviderException("SEARCH_ERROR", "标准搜索 API 请求失败") : last;
    }

    private String readResponse(HttpURLConnection connection, int status) throws Exception {
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) return "";
        try (stream) { return new String(stream.readNBytes(4_000_000), StandardCharsets.UTF_8); }
    }

    private SearchProviderException httpFailure(int status, String response) {
        String type = switch (status) {
            case 401, 403 -> "AUTHENTICATION_FAILED";
            case 429 -> "RATE_LIMITED";
            default -> status >= 500 ? "UPSTREAM_ERROR" : "HTTP_" + status;
        };
        return new SearchProviderException(type, "HTTP " + status + preview(response));
    }

    private boolean retriable(int status) { return status == 429 || status >= 500; }

    private void boundedBackoff(int attempt) {
        try { Thread.sleep(150L * (attempt + 1)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    private void recordFailure(long started, String type, String message) {
        health = new SearchProviderHealth(id(), "UNAVAILABLE", elapsed(started), health.lastSuccess(),
                safe(message), type);
        lastProbe = Instant.now();
    }

    private String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) throw new SearchProviderException("INVALID_QUERY", "搜索词不能为空");
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }

    protected final String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("(?i)(tvly-|BSA)[A-Za-z0-9_-]{8,}", "***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
    }

    private String preview(String value) {
        if (value == null || value.isBlank()) return "";
        String compact = safe(value).replaceAll("\\s+", " ");
        return ": " + (compact.length() > 240 ? compact.substring(0, 240) + "…" : compact);
    }
}
