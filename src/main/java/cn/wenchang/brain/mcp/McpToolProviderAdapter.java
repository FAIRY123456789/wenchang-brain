package cn.wenchang.brain.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Spring AI MCP Client 与文昌 Tool Registry 之间的可插拔边界。
 *
 * <p>starter 会把远端 MCP tools 转为 SyncMcpToolCallbackProvider；本类不重写协议或工具，
 * 只在 Provider 存在时发现回调。spring.ai.mcp.client.enabled=false 时没有 Provider，返回空数组。</p>
 */
@Component
public class McpToolProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(McpToolProviderAdapter.class);
    private final ObjectProvider<SyncMcpToolCallbackProvider> provider;

    public McpToolProviderAdapter(ObjectProvider<SyncMcpToolCallbackProvider> provider) {
        this.provider = provider;
    }

    public ToolCallback[] discoverTools() {
        SyncMcpToolCallbackProvider available = provider.getIfAvailable();
        if (available == null) return new ToolCallback[0];
        try {
            ToolCallback[] callbacks = available.getToolCallbacks();
            return callbacks == null ? new ToolCallback[0] : callbacks;
        } catch (RuntimeException exception) {
            log.warn("MCP tool discovery failed: {}", exception.getClass().getSimpleName());
            return new ToolCallback[0];
        }
    }
}
