package cn.wenchang.brain.tool;

import cn.wenchang.brain.search.SearchProvider;
import cn.wenchang.brain.search.SearchProviderException;
import cn.wenchang.brain.search.SearchProviderHealth;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebSearchProviderContractTest {

    @Test
    void exposesAntiBotAsUnavailableInsteadOfReady() {
        SearchProvider provider = new SearchProvider() {
            private final SearchProviderHealth health = new SearchProviderHealth(
                    "sogou", "UNAVAILABLE", 17, null, "redirect to anti-bot challenge", "ANTI_BOT");
            @Override public String id() { return "sogou"; }
            @Override public List<WebSearchResult> search(String query, int limit) {
                throw new SearchProviderException("ANTI_BOT", "redirect to anti-bot challenge");
            }
            @Override public SearchProviderHealth healthCheck() { return health; }
            @Override public SearchProviderHealth currentHealth() { return health; }
        };
        WebSearchTool tool = new WebSearchTool(provider);

        assertThat(tool.webSearch("文昌最新政策"))
                .contains("status=UNAVAILABLE", "errorType=ANTI_BOT")
                .doesNotContain("READY");
        assertThat(tool.healthCheck().available()).isFalse();
    }
}
