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
                .contains("data-language-value=\"zh-CN\"", "data-language-value=\"en\"", "data-language-value=\"id\"", "data-language-value=\"ar\"", "data-language-value=\"pt\"")
                .contains("styles.css?v=1.5.1-language-ui", "i18n.js?v=1.5.1-language-ui", "app.js?v=1.5.1-language-ui")
                .containsSubsequence("vendor/purify.min.js", "i18n.js", "app.js");
        assertThat(i18n)
                .contains("const DEFAULT_LANGUAGE = 'zh-CN'")
                .contains("const SUPPORTED = ['zh-CN', 'en', 'id', 'ar', 'pt']")
                .contains("wenchang-system-language")
                .contains("let activeLanguage = DEFAULT_LANGUAGE")
                .contains("activeLanguage = language")
                .contains("data-language-value")
                .contains("localStorage.setItem(STORAGE_KEY, language)")
                .contains("document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr'");
        assertThat(i18n).containsSubsequence(
                "function setLanguage(value)",
                "activeLanguage = language",
                "localStorage.setItem(STORAGE_KEY, language)",
                "apply(document)");
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
                .doesNotContain("languageInput.addEventListener('change'")
                .contains("window.addEventListener('wenchang:languagechange'")
                .contains("state.language = event.detail?.language")
                .contains("provider: $('providerInput').value")
                .doesNotContain("language: $('languageInput').value");
        assertThat(css)
                .contains(".system-language-card")
                .contains(".language-options button.is-active")
                .contains("backdrop-filter: blur(18px)")
                .contains("html[dir=\"rtl\"] .settings-drawer")
                .contains("html[dir=\"rtl\"] .composer textarea");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(STATIC_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
