package cn.wenchang.brain.search;

import cn.wenchang.brain.tool.WebSearchResult;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ordered standard-provider failover with TTL cache, URL de-duplication and a small circuit breaker.
 * It never attempts CAPTCHA bypass, proxy rotation, or identity spoofing.
 */
public final class ResilientSearchProvider implements SearchProvider {

    private final List<SearchProvider> providers;
    private final Duration cacheTtl;
    private final int failureThreshold;
    private final Duration circuitCooldown;
    private final Map<String, CachedResults> cache = new ConcurrentHashMap<>();
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();
    private volatile SearchProviderHealth health;

    public ResilientSearchProvider(List<SearchProvider> providers, Duration cacheTtl,
                                   int failureThreshold, Duration circuitCooldown) {
        this.providers = List.copyOf(providers);
        this.cacheTtl = cacheTtl.isNegative() ? Duration.ZERO : cacheTtl;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.circuitCooldown = circuitCooldown.isNegative() ? Duration.ZERO : circuitCooldown;
        this.health = new SearchProviderHealth(id(), "UNKNOWN", 0, null,
                "尚未执行搜索；候选 Provider=" + providerNames(), "NOT_CHECKED");
    }

    @Override public String id() { return "auto[" + providerNames() + "]"; }

    @Override
    public List<WebSearchResult> search(String query, int requestedLimit) {
        long started = System.nanoTime();
        int limit = Math.max(1, Math.min(20, requestedLimit));
        String key = normalize(query).toLowerCase(Locale.ROOT) + "|" + limit;
        CachedResults hit = cache.get(key);
        if (hit != null && Duration.between(hit.createdAt(), Instant.now()).compareTo(cacheTtl) < 0) {
            health = new SearchProviderHealth(id() + ":cache", "AVAILABLE", elapsed(started),
                    hit.createdAt(), "", "");
            return hit.results();
        }

        List<String> failures = new ArrayList<>();
        String lastType = "SEARCH_PROVIDER_UNAVAILABLE";
        for (SearchProvider provider : providers) {
            CircuitState circuit = circuits.computeIfAbsent(provider.id(), ignored -> new CircuitState());
            if (circuit.isOpen()) {
                failures.add(provider.id() + "=CIRCUIT_OPEN");
                lastType = "CIRCUIT_OPEN";
                continue;
            }
            try {
                List<WebSearchResult> results = deduplicate(provider.search(query, limit), limit);
                circuit.success();
                if (results.isEmpty()) {
                    failures.add(provider.id() + "=NO_RESULTS");
                    continue;
                }
                List<WebSearchResult> immutable = List.copyOf(results);
                cache.put(key, new CachedResults(Instant.now(), immutable));
                health = new SearchProviderHealth(id() + ":" + provider.id(), "AVAILABLE",
                        elapsed(started), Instant.now(), failures.isEmpty() ? "" : String.join("; ", failures), "");
                return immutable;
            } catch (SearchProviderException exception) {
                circuit.failure(failureThreshold, circuitCooldown);
                lastType = exception.errorType();
                failures.add(provider.id() + "=" + exception.errorType() + ":" + safe(exception.getMessage()));
            } catch (RuntimeException exception) {
                circuit.failure(failureThreshold, circuitCooldown);
                lastType = exception.getClass().getSimpleName();
                failures.add(provider.id() + "=" + lastType + ":" + safe(exception.getMessage()));
            }
        }
        String detail = failures.isEmpty() ? "没有可用的标准搜索 API Provider" : String.join("; ", failures);
        health = new SearchProviderHealth(id(), "UNAVAILABLE", elapsed(started), health.lastSuccess(), detail, lastType);
        throw new SearchProviderException(lastType, detail);
    }

    @Override
    public SearchProviderHealth healthCheck() {
        long started = System.nanoTime();
        List<String> states = new ArrayList<>();
        for (SearchProvider provider : providers) {
            if (circuits.computeIfAbsent(provider.id(), ignored -> new CircuitState()).isOpen()) {
                states.add(provider.id() + "=CIRCUIT_OPEN");
                continue;
            }
            SearchProviderHealth candidate = provider.healthCheck();
            states.add(provider.id() + "=" + candidate.health());
            if (candidate.available()) {
                health = new SearchProviderHealth(id() + ":" + provider.id(), "AVAILABLE", elapsed(started),
                        candidate.lastSuccess(), "", "");
                return health;
            }
        }
        health = new SearchProviderHealth(id(), "UNAVAILABLE", elapsed(started), health.lastSuccess(),
                String.join("; ", states), states.isEmpty() ? "SEARCH_API_NOT_CONFIGURED" : "ALL_PROVIDERS_UNAVAILABLE");
        return health;
    }

    @Override public SearchProviderHealth currentHealth() { return health; }

    @Override
    public String resolveExternalUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return "";
        return rawUrl;
    }

    private List<WebSearchResult> deduplicate(List<WebSearchResult> input, int limit) {
        Map<String, WebSearchResult> unique = new LinkedHashMap<>();
        for (WebSearchResult result : input) {
            String key = canonicalUrl(result.url());
            if (key.isBlank()) key = result.title().strip().toLowerCase(Locale.ROOT);
            unique.putIfAbsent(key, result);
            if (unique.size() >= limit) break;
        }
        return new ArrayList<>(unique.values());
    }

    private String canonicalUrl(String raw) {
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            return host + path;
        } catch (Exception ignored) { return raw == null ? "" : raw; }
    }

    private String normalize(String query) {
        String value = query == null ? "" : query.replaceAll("\\s+", " ").trim();
        if (value.isBlank()) throw new SearchProviderException("INVALID_QUERY", "搜索词不能为空");
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private String providerNames() { return providers.stream().map(SearchProvider::id).reduce((a, b) -> a + "," + b).orElse("none"); }
    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private String safe(String value) { return value == null ? "unknown" : value.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***"); }

    private record CachedResults(Instant createdAt, List<WebSearchResult> results) { }

    private static final class CircuitState {
        private int failures;
        private Instant openUntil;
        synchronized boolean isOpen() {
            if (openUntil == null) return false;
            if (Instant.now().isAfter(openUntil)) { failures = 0; openUntil = null; return false; }
            return true;
        }
        synchronized void success() { failures = 0; openUntil = null; }
        synchronized void failure(int threshold, Duration cooldown) {
            failures++;
            if (failures >= threshold) openUntil = Instant.now().plus(cooldown);
        }
    }
}
