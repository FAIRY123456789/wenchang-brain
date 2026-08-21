package cn.wenchang.brain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SystemLanguageUiContractTest {

    private static final Path STATIC_ROOT = Path.of("src", "main", "resources", "static");

    @Test
    void settingsExposeFivePersistedSystemLanguagesWithChineseAsDefault() throws IOException {
        String html = read("index.html");
        String i18n = read("i18n.js");

        assertThat(html)
                .contains("id=\"languageInput\"")
                .contains("value=\"zh-CN\"", "value=\"en\"", "value=\"id\"", "value=\"ar\"", "value=\"pt\"")
                .contains("中文（默认）", "English", "Bahasa Indonesia", "العربية", "Português")
                .containsSubsequence("vendor/purify.min.js", "i18n.js", "app.js");
        assertThat(i18n)
                .contains("const DEFAULT_LANGUAGE = 'zh-CN'")
                .contains("const SUPPORTED = ['zh-CN', 'en', 'id', 'ar', 'pt']")
                .contains("wenchang-system-language")
                .contains("localStorage.setItem(STORAGE_KEY, language)")
                .contains("document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr'");
    }

    @Test
    void languageSwitchUpdatesStaticAndDynamicUiWithoutChangingModelPayload() throws IOException {
        String html = read("index.html");
        String app = read("app.js");
        String css = read("styles.css");

        assertThat(html).contains(
                "data-i18n=\"settings.title\"",
                "data-i18n=\"hero.line1\"",
                "data-i18n-placeholder=\"composer.placeholder\"",
                "data-i18n=\"language.title\"");
        assertThat(app)
                .contains("languageInput.addEventListener('change'")
                .contains("window.addEventListener('wenchang:languagechange'")
                .contains("state.language = event.detail?.language")
                .contains("provider: $('providerInput').value")
                .doesNotContain("language: $('languageInput').value");
        assertThat(css)
                .contains(".system-language-card")
                .contains("html[dir=\"rtl\"] .settings-drawer")
                .contains("html[dir=\"rtl\"] .composer textarea");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(STATIC_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
