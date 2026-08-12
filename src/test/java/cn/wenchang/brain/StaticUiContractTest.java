package cn.wenchang.brain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StaticUiContractTest {

    private static final Path STATIC_ROOT = Path.of("src", "main", "resources", "static");

    @Test
    void initialMarkupStartsLoadingAndUsesOneLogoAssetEverywhere() throws IOException {
        String html = read("index.html");
        String app = read("app.js");
        String logo = read("assets/wenchang-logo.svg");

        assertThat(html)
                .contains("data-app-state=\"APP_LOADING\"")
                .contains("data-hydrated=\"false\"")
                .contains("href=\"assets/wenchang-logo.svg\"")
                .contains("src=\"assets/wenchang-logo.svg\"")
                .contains("placeholder=\"问文昌智脑任何问题...\"");
        assertThat(app).contains("logo.src = appUrl('/assets/wenchang-logo.svg')");
        assertThat(app).contains("function sourceDetails(sources)", "function toolLabel(tool)",
                "data.files ?? data.sourceFiles ?? 0", "message.toolsJson", "sources, toolsUsed");
        assertThat(logo).contains("<svg", "海岸线", "火箭轨迹");
    }

    @Test
    void hydrationAndHeroAnimationHaveExplicitStateContracts() throws IOException {
        String css = read("styles.css");
        String app = read("app.js");

        assertThat(css)
                .contains("html[data-app-state=\"APP_LOADING\"] .app-frame")
                .contains("html[data-hero-transition=\"true\"] .hero")
                .contains("@media (prefers-reduced-motion: reduce)");
        assertThat(app)
                .contains("finishHydration('CHAT')")
                .contains("finishHydration('HOME')")
                .contains("transitionHomeToChat()")
                .contains("state.appState === 'HOME'");
    }

    @Test
    void sidebarComposerAndSettingsKeepAccessibilityContracts() throws IOException {
        String html = read("index.html");
        String css = read("styles.css");
        String app = read("app.js");

        assertThat(html)
                .contains("aria-label=\"关闭侧栏\"")
                .contains("aria-expanded=\"false\"")
                .contains("aria-hidden=\"true\" inert");
        assertThat(css)
                .contains(".sidebar .sidebar-close { display: none")
                .contains(".composer textarea:focus-visible")
                .contains("box-shadow: none")
                .contains("width: min(920px")
                .contains("font-size: 17px");
        assertThat(app)
                .contains("settingsDrawer.inert = false")
                .contains("settingsDrawer.inert = true")
                .doesNotContain("本地演示模型");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(STATIC_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
