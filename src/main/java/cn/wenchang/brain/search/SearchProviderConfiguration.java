package cn.wenchang.brain.search;

import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.tool.WebSearchResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
public class SearchProviderConfiguration {

    @Bean
    SearchProvider searchProvider(WenchangProperties properties) {
        WenchangProperties.WebSearch config = properties.getWebSearch();
        String requested = normalize(config.getProvider(), "auto");
        if (!config.isEnabled()) {
            return new UnavailableSearchProvider(requested, "SEARCH_DISABLED", "联网搜索已在配置中禁用");
        }
        if ("sogou".equals(requested)) return new SogouSearchProvider(properties);
        if ("tavily".equals(requested)) return configuredTavily(config);
        if ("brave".equals(requested)) return configuredBrave(config);
        if ("api".equals(requested) || "standard-api".equals(requested)) requested = "auto";
        if (!"auto".equals(requested)) {
            return new UnavailableSearchProvider(requested, "SEARCH_PROVIDER_UNSUPPORTED",
                    "未安装搜索 Provider：" + requested);
        }

        List<SearchProvider> providers = new ArrayList<>();
        for (String name : normalize(config.getFallbackOrder(), "tavily,brave").split(",")) {
            String candidate = normalize(name, "");
            if ("tavily".equals(candidate) && hasText(tavilyKey(config))) {
                providers.add(new TavilySearchProvider(config.getTavilyEndpoint(), tavilyKey(config), config.getTimeoutSeconds()));
            } else if ("brave".equals(candidate) && hasText(config.getBraveApiKey())) {
                providers.add(new BraveSearchProvider(config.getBraveEndpoint(), config.getBraveApiKey(), config.getTimeoutSeconds()));
            } else if ("sogou".equals(candidate) && config.isAllowHtmlFallback()) {
                providers.add(new SogouSearchProvider(properties));
            }
        }
        if (config.isAllowHtmlFallback() && providers.stream().noneMatch(p -> "sogou".equals(p.id()))) {
            providers.add(new SogouSearchProvider(properties));
        }
        if (providers.isEmpty()) {
            return new UnavailableSearchProvider("auto", "SEARCH_API_NOT_CONFIGURED",
                    "未配置标准搜索 API。请设置 WENCHANG_TAVILY_API_KEY 或 WENCHANG_BRAVE_API_KEY");
        }
        return new ResilientSearchProvider(providers,
                Duration.ofSeconds(Math.max(0, config.getCacheTtlSeconds())),
                config.getCircuitFailureThreshold(),
                Duration.ofSeconds(Math.max(0, config.getCircuitCooldownSeconds())));
    }

    private SearchProvider configuredTavily(WenchangProperties.WebSearch config) {
        String key = tavilyKey(config);
        return hasText(key)
                ? new TavilySearchProvider(config.getTavilyEndpoint(), key, config.getTimeoutSeconds())
                : new UnavailableSearchProvider("tavily", "SEARCH_API_NOT_CONFIGURED", "Tavily API Key 未配置");
    }

    private SearchProvider configuredBrave(WenchangProperties.WebSearch config) {
        return hasText(config.getBraveApiKey())
                ? new BraveSearchProvider(config.getBraveEndpoint(), config.getBraveApiKey(), config.getTimeoutSeconds())
                : new UnavailableSearchProvider("brave", "SEARCH_API_NOT_CONFIGURED", "Brave Search API Key 未配置");
    }

    /** WENCHANG_SEARCH_API_KEY remains a compatibility alias for the first/default Tavily provider. */
    private String tavilyKey(WenchangProperties.WebSearch config) {
        return hasText(config.getTavilyApiKey()) ? config.getTavilyApiKey() : config.getApiKey();
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
    }

    static final class UnavailableSearchProvider implements SearchProvider {
        private final SearchProviderHealth health;
        UnavailableSearchProvider(String provider, String errorType, String message) {
            this.health = new SearchProviderHealth(provider, "UNAVAILABLE", 0, null, message, errorType);
        }
        @Override public String id() { return health.provider(); }
        @Override public List<WebSearchResult> search(String query, int limit) {
            throw new SearchProviderException(health.errorType(), health.lastError());
        }
        @Override public SearchProviderHealth healthCheck() { return health; }
        @Override public SearchProviderHealth currentHealth() { return health; }
    }
}
