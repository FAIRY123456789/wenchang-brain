package cn.wenchang.brain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SubpathDeploymentContractTest {

    private static final Path STATIC = Path.of("src", "main", "resources", "static");

    @Test
    void supportsRootAndWenchangBrainSubpathWithoutAbsoluteStaticOrApiRequests() throws Exception {
        String html = Files.readString(STATIC.resolve("index.html"), StandardCharsets.UTF_8);
        String app = Files.readString(STATIC.resolve("app.js"), StandardCharsets.UTF_8);

        assertThat(html).contains("href=\"assets/wenchang-logo.svg\"", "href=\"styles.css?v=1.5.1-language-ui\"",
                "src=\"vendor/marked.min.js\"", "src=\"vendor/purify.min.js\"",
                "src=\"i18n.js?v=1.5.1-language-ui\"", "src=\"app.js?v=1.5.1-language-ui\"")
                .doesNotContain("href=\"/assets/", "href=\"/styles.css", "src=\"/vendor/", "src=\"/app.js");
        assertThat(app).contains("const APP_BASE_PATH", "const appUrl", "appUrl('/api/chat/stream')",
                "fetch(appUrl(url), options)", "appUrl(`/api/artifacts/")
                .doesNotContain("fetch('/api/", "fetch(`/api/");
    }
}
