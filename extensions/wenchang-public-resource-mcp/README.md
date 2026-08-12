# 文昌公共资源 MCP Server

文昌智脑 V1.4 的独立 Spring Boot 服务，通过 Spring AI 2.0 WebMVC Streamable HTTP 暴露结构化公共资源查询能力。它不包含模型密钥，也不依赖主应用进程；主应用作为 MCP Client 连接即可发现和调用工具。

## 服务端点

- 服务地址：`http://127.0.0.1:8091`
- MCP Streamable HTTP：`POST/GET/DELETE /mcp`
- 健康状态：`GET /actuator/health`
- 构建版本：`GET /actuator/info`

Spring AI 2.0 的官方配置方式是使用 `spring-ai-starter-mcp-server-webmvc` 并将 `spring.ai.mcp.server.protocol` 设置为 `STREAMABLE`。参见 [Spring AI Streamable HTTP MCP Server 文档](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html)。

## MCP Tools

| Tool | 参数 | 数据资产 |
| --- | --- | --- |
| `searchPublicServices` | `keyword?`, `category?`, `town?` | `data/wenchang-public-services.json` |
| `searchTownshipProfile` | `town` | `data/wenchang-townships.json`，并关联公共服务与研学地点 |
| `searchStudyTourPlaces` | `theme?`, `town?`, `ageGroup?` | `data/wenchang-places.json` |

工具结果包含数据状态、实际查询时间、数据文件、匹配记录及动态核验提示。公共服务开放、医疗接诊、交通运行、研学预约、天气和安全管制等动态信息必须以主管单位最新公告为准。

## 数据文件约定

默认会依次探测：

1. 当前工作目录下的 `data/`；
2. 从本扩展目录运行时的 `../../data/`。

生产环境建议显式设置绝对路径：

```powershell
$env:WENCHANG_DATA_ROOT='D:\Desktop\文昌智脑\data'
```

也可分别覆盖文件名：

- `WENCHANG_PUBLIC_SERVICES_FILE`
- `WENCHANG_TOWNSHIPS_FILE`
- `WENCHANG_PLACES_FILE`
- `WENCHANG_MCP_MAX_RESULTS`（1–100，默认 20）

服务默认只监听 `127.0.0.1`。只有明确需要远程部署时才设置 `SERVER_ADDRESS`，并应在上游网关增加认证与访问控制。

根节点兼容以下数组键：

- 公共服务：`services`、`publicServices`、`public_services`
- 乡镇：`townships`、`administrativeUnits`、`administrative_units`、`units`
- 地点：`places`

字段读取兼容 camelCase、snake_case 及既有 `district`/`suitableAge` 别名。文件缺失或 JSON 读取失败不会造成服务启动崩溃，健康详情与工具响应会明确返回 `MISSING` 或 `ERROR`。

## 构建、测试与启动

在扩展目录执行：

```powershell
mvn --% -Dmaven.repo.local=../../.m2/repository clean package
java -jar target/wenchang-public-resource-mcp-1.4.0-SNAPSHOT.jar
```

测试包含真实 MCP SDK Client 的 `initialize → tools/list → tools/call` Streamable HTTP 链路，不只是直接调用 Java 方法。

## 主应用连接配置

主应用 `config/mcp-servers.yml`：

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        initialized: true
        type: SYNC
        request-timeout: 20s
        toolcallback:
          enabled: true
        streamable-http:
          connections:
            wenchang-public-resource:
              url: http://127.0.0.1:8091
              endpoint: /mcp
```

先启动本服务，再启动文昌智脑主应用。主应用初始化后应能通过 `/api/agent/tools` 看到三个 MCP 工具。

## 协议验收

```powershell
mvn --% -Dmaven.repo.local=../../.m2/repository -Dtest=McpStreamableHttpIntegrationTest test
```

该测试启动随机 HTTP 端口，使用官方 Java MCP SDK 建立真实 Streamable HTTP 会话，验证服务器名称、精确的三个工具名以及 `searchPublicServices` 的实际调用结果。
