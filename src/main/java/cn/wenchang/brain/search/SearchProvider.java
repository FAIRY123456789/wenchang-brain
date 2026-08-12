package cn.wenchang.brain.search;

import cn.wenchang.brain.tool.WebSearchResult;

import java.util.List;

/** 可替换的联网搜索边界；工具层不依赖具体搜索网页或商业 API。 */
public interface SearchProvider {

    String id();

    List<WebSearchResult> search(String query, int limit);

    SearchProviderHealth healthCheck();

    SearchProviderHealth currentHealth();

    default String resolveExternalUrl(String rawUrl) { return rawUrl == null ? "" : rawUrl; }
}
