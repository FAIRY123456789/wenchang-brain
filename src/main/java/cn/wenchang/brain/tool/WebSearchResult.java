package cn.wenchang.brain.tool;

/** Minimal, provider-neutral web result with optional recency/provenance metadata. */
public record WebSearchResult(
        String title,
        String url,
        String snippet,
        String publishedAt,
        String sourceProvider
) {
    public WebSearchResult(String title, String url, String snippet) {
        this(title, url, snippet, "", "");
    }

    public WebSearchResult {
        title = title == null ? "" : title;
        url = url == null ? "" : url;
        snippet = snippet == null ? "" : snippet;
        publishedAt = publishedAt == null ? "" : publishedAt;
        sourceProvider = sourceProvider == null ? "" : sourceProvider;
    }
}
