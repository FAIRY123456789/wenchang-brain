# 文昌智脑 V1.5

文昌智脑是面向文昌科普、研学、政策、公共服务和城市研究的任务型知识智能体。用户可以在同一个 Composer 中用 `@` 选择专业智能体、用 `/` 选择生产技能，再由 DeepSeek、可追溯知识库、Native Tool 与独立 MCP 任务服务共同完成研究、规划、政策整理和文件生成任务。任务过程、来源、Agent Run、人工确认与 Artifact 均可追踪和恢复。

## V1.5 架构

```text
Browser UI (@Agent / Skill / Agent Run / Sources / Files)
  -> Conversation API + named SSE events
  -> AgentProfileRegistry + SkillRegistry
  -> RAG + CapabilityRouter + WenchangToolRegistry
       -> Native: web / official / evidence / place / policy / material collection
       -> MCP Client -> 文昌任务 MCP Server :8091/mcp
          -> public resources / Word / CSV / XLSX / study tour / policy brief
  -> DeepSeek ChatModel
  -> H2 Conversation + Message + AgentRun + AgentStep + Approval persistence

data/artifacts/{conversationId}/
  -> manifest metadata
  -> GET /api/artifacts/**

Markdown + Front Matter
  -> recursive ingestion + deduplication
  -> Document / Section / Chunk
  -> deterministic embedding + SimpleVectorStore
```

未配置 DeepSeek API Key 时，页面和聊天接口统一显示“模型未配置”，并引导进入模型设置；不会在正式交互中自动切换为面向用户的替代模型。开发测试仍使用确定性 Stub 与 Mock，知识库向量化不依赖远程密钥。

设置栏提供本地持久化的系统语言选项，默认中文，并可切换 English、Bahasa Indonesia、العربية 与 Português。语言切换即时作用于首页、Command Bar、设置、系统状态与详情面板，不修改服务端模型配置；阿拉伯语自动启用 RTL 布局，刷新页面后继续保持用户选择。

## Agent Command Experience

Composer 上方的 Command Bar 是智能体与技能的统一入口：默认状态只显示“`@` 选择智能体”和“`/` 使用技能”；点击或在输入框键入命令后，就地展开文字优先的选择器，不跳转页面，也不提前创建空会话。Agent 与 Skill 可以独立选择、组合使用、切换或移除；只有用户真正发送消息时才创建会话并执行任务。桌面端采用三列卡片，移动端采用两列卡片，支持方向键、Enter、Tab 与 Esc。

Agent 详情使用英文名称和中文能力说明，展示能完成的任务、适合输入、技能、工具、输出文件、典型流程、人工确认规则、示例和完成判据。“开始使用”只把 Agent 带回当前 Composer；历史会话恢复时会恢复所选 Agent、Skill、Agent Run 与 Artifact。

用户消息支持原位编辑与问题版本切换。编辑态沿用单一蓝色消息气泡作为输入表面，内部输入区透明无边框；Enter 发送、Shift+Enter 换行、Esc 取消，编辑后的分支拥有独立历史、工具结果与 Artifact。

## Agent Profile

- `wenchang`：Wenchang Assistant，综合文昌知识、实时信息与任务执行。
- `aerospace`：Aerospace Researcher，面向文昌航天、商业航天与发射任务研究。
- `ecology`：Ecology Researcher，面向海岸带、红树林与生态环境研究。
- `study-tour`：Study Tour Planner，设计文昌航天、生态与文化研学任务。
- `policy`：Policy Assistant，查询、核验和整理文昌及海南相关政策。

## Skill

- 研究：`/联网搜索`、`/权威检索`、`/证据核验`、`/政策检索`、`/最新政策`、`/政策对比`、`/深度研究`。
- 工作成果：`/生成Word`、`/导出数据`、`/政策简报`、`/研学方案`。
- 数据与地点：`/地点查询`、`/公共服务`。

每项 Skill 都声明输入、工具、输出类型与审批策略。`/深度研究` 展示公开计划，调用知识、官方、联网、专题采集与 Word 工具；`/研学方案` 使用真实地点和实际坐标形成路线并生成 Word，不虚构精确驾车时间。

## Tool Registry

Native Tool：

- `webSearch`
- `officialSourceSearch`
- `knowledgeEvidence`
- `placeSearch`
- `policySearch`
- `collectOfficialMaterials`

MCP Tool：

- `searchPublicServices`
- `searchTownshipProfile`
- `searchStudyTourPlaces`
- `createWenchangWordReport`
- `exportWenchangData`
- `createStudyTourPackage`
- `createPolicyBrief`

工具目录：`GET /api/agent/tools`。返回项直接来自 Spring AI ToolDefinition，并标记 `source=NATIVE|MCP`。Trace 记录 `toolName / toolSource / stage / input / status / errorType / output / latencyMs / traceId`，不记录模型内部推理。`diagnosticEcho` 只存在于生产自检，不进入普通 Registry。

## Agent Task Architecture

每个 Agent Profile 定义任务范围、适合输入、可调用工具、可生成文件、典型过程、审批规则、示例任务与完成判据。Agent 不再占用侧栏；用户从 Command Bar 的 `@` 选择器进入能力详情或选中 Agent，Composer 上方显示上下文卡并可随时重新查看能力，全程不会提前创建空会话。

长期资产操作由 `AgentApproval` 管理：`preview → PENDING → confirm/cancel → EXECUTED/CANCELLED/FAILED`。知识库重新索引、政策知识批量刷新等操作必须确认；用户在请求中明确要求 Word 或数据导出时，请求本身即为文件生成确认。

`AgentRun` 与 `AgentStep` 独立持久化，记录会话、Agent、Skill、状态、步骤、工具来源、耗时、摘要与错误；消息仍保存 Agent Run JSON 快照，页面刷新后可恢复任务摘要和逐步详情。

## Artifact Architecture

独立 MCP Server 使用 Apache POI 真实生成 DOCX/XLSX，并生成 UTF-8 CSV。文件与 manifest 保存在 `data/artifacts/{conversationId}/`；manifest 记录 artifactId、文件名、类型、创建时间、Agent、Skill、来源数、内容类型、大小与相对路径。

主应用提供 `GET /api/artifacts`、`GET /api/artifacts/{id}`、`GET /api/artifacts/{id}/download`、`DELETE /api/artifacts/{id}`。下载使用 UTF-8 文件名，文件路径经过 normalize、root 边界与 real path 检查。ChatResponse 与 Message 持久化 Artifact metadata，历史会话恢复后文件卡仍存在。

### Artifact Output

Agent 可以生成 Word、Excel 与 CSV。文件生成成功后，统一的 `ArtifactDescriptor` 会进入 Chat Response、SSE `complete`、Agent Run 与 Message 持久化，聊天正文下方立即显示可点击的文件卡；刷新会话或重启服务后仍可下载。公网部署通过统一 Base Path 生成 `/wenchang-brain/api/artifacts/{id}/download`，文件保存在独立于 Release 的持久目录。

## MCP Architecture

`extensions/wenchang-public-resource-mcp` 是独立 Spring AI Streamable HTTP Server，默认运行于 `127.0.0.1:8091/mcp`。前三个工具提供公共服务、乡镇画像和研学地点查询；四个生产工具生成 Word、CSV/XLSX、研学方案和政策简报。主应用通过 Spring AI MCP Client 动态 `tools/list`，MCP 断开不会被误报为联网搜索故障。

生成型 Artifact 使用面向读者的内容模板：Word 会将结构化资料整理成专业标题、正文层级、名单表格、核验提示与去重来源，不写入工具 JSON 或原始指令；Excel 使用中文表头、标题区、筛选、冻结窗格、适配列宽与可点击来源；CSV 使用 UTF-8 BOM 和中文表头。生成成功后，聊天回答会直接附带 Markdown 下载超链接，并保留文件卡片与历史恢复。

## Markdown Rendering

Assistant 内容唯一经过本地 `marked` 与 `DOMPurify`：`marked.parse → DOMPurify.sanitize → DOM`。SSE 使用 `rawMarkdownBuffer` 与短 debounce 增量重绘，complete 事件做最终完整渲染；历史消息与刷新恢复走同一 Renderer。支持 H1-H4、粗体、斜体、列表、链接、引用、行内代码、代码块、表格和分隔线；外链统一使用 `target=_blank` 与 `rel=noopener noreferrer`，页面不依赖 CDN。

## Diagnostics

`GET /api/admin/diagnostics/agent` 真实探测 DeepSeek、隔离 Tool Calling、Native Tool、MCP 发现、SearchProvider、官方检索、RAG 与 Artifact 工具。设置页“运行智能体自检”展示模型连接、知识库、联网搜索、权威检索、MCP、Word 和数据导出的可用性、异常与耗时。

SearchProvider 提供 `healthCheck()` 与 `search()`。生产默认使用 `auto` 标准 API 路由，支持 Tavily 与 Brave Search 两个 JSON API、按序故障转移、限次重试、TTL 缓存、URL 去重和单 Provider 熔断。搜狗 HTML Provider 只在显式设置 `WENCHANG_SEARCH_ALLOW_HTML_FALLBACK=true` 时参与降级；反爬/重定向会明确报告 `UNAVAILABLE / ANTI_BOT`，不会伪装成 READY，也不会绕过验证码或使用代理池规避站点防护。

至少配置一个正式搜索 Key：`WENCHANG_TAVILY_API_KEY` 或 `WENCHANG_BRAVE_API_KEY`。可通过 `WENCHANG_SEARCH_FALLBACK_ORDER=tavily,brave` 调整优先级；`WENCHANG_SEARCH_API_KEY` 继续作为 Tavily 首选 Key 的兼容别名。DeepSeek Chat API 负责 Tool Calling 决策，实际网页检索由这些 SearchProvider 执行。

## Harness Engineering

Harness 同时检查 Tool 选择、来源、Agent Run、Artifact metadata、文件存在/可打开、下载、审批与 UI 静态契约。固定 RAG Eval 继续覆盖 25 个知识与工具用例；Agent Eval 还包括政策简报、研学方案、Excel 导出和深度研究 Word 四项生产任务。

## Agent Run 与持久化

`POST /api/chat/stream` 接收 `message / conversationId / agentId / skillId`，发送以下命名事件：

```text
agent_selected / skill_selected / plan_created
step_started / tool_started / tool_completed / source_found
answer_chunk / step_completed / complete
```

前端完成后折叠为步骤、工具和来源摘要。Conversation 与 Message 持久化 `agentId / skillId / agentRunJson / toolsUsedJson / sourcesJson`；刷新或切换历史会话后可恢复 Agent Chip、Skill Chip、任务步骤、工具标签和来源。

## 深度知识资产

- Active Markdown：50；Document / Chunk：136 / 136。
- 乡镇：17 条结构化画像与 17 份独立 Markdown。
- 公共服务：24 条，覆盖医疗、教育、文化、体育、政务、交通、科普和应急等类别。
- 政策：10 条，统一使用 `CURRENT / EXPIRED / SUPERSEDED / UNKNOWN` 状态。
- 地点：25 条；全部具有坐标、研学年龄、学习要点与来源。
- Sources Index：89 条唯一来源，P0 / P1 / P2 = 46 / 39 / 4；active 50、supporting 39。
- 官方来源注册表：21 个域名规则。

原地点集中的文昌航天超算中心、宋氏祖居、东郊椰林、铺前骑楼老街缺少可可靠交叉核验的坐标。多表达检索后仍不能满足 active 数据标准，因此四项已从结构化地点集移除，没有写入推测坐标；知识文档中的相关背景不受影响。

关键资产：

- `knowledge/SOURCES_INDEX.csv`
- `data/wenchang-townships.json`
- `data/wenchang-public-services.json`
- `data/wenchang-policies.json`
- `data/wenchang-places.json`
- `data/official-source-registry.json`

所有 active Markdown 保留 Front Matter 与 `source_id / source_organization / source_url / source_level / published_at / retrieved_at / tags / index_status`。摄取阶段按 canonical URL、标题、内容哈希和近重复内容去重，并保留相对文件路径与章节元数据。

## 政策刷新

`POST /api/admin/knowledge/refresh-policies` 对预设政策主题执行官方来源发现，返回新增候选 URL，并重新索引已审核语料。为避免搜索摘要污染正式知识，候选政策必须打开官方原文并经人工核验后，才可进入 Policy JSON、Markdown 与 Sources Index。

## 正式模型配置

复制示例并填写真实密钥：

```powershell
Copy-Item config/local-secrets.properties.example config/local-secrets.properties
```

```properties
wenchang.ai.default.provider=deepseek
wenchang.ai.default.base-url=https://api.deepseek.com
wenchang.ai.default.api-key=填写真实密钥
wenchang.ai.default.model=deepseek-chat
```

浏览器只接收脱敏状态，不返回 API Key。运行时设置优先于服务端 DeepSeek 配置；清除运行时设置后恢复服务端配置。

## 构建与启动

要求 JDK 17 与 Maven 3.9+。先启动独立 MCP Server：

```powershell
mvn --% -f extensions/wenchang-public-resource-mcp/pom.xml -Dmaven.repo.local=.m2/repository clean package
java -jar extensions/wenchang-public-resource-mcp/target/wenchang-public-resource-mcp-1.4.0-SNAPSHOT.jar
```

再启动主应用并启用 MCP profile：

```powershell
mvn --% -Dmaven.repo.local=.m2/repository clean package
java -jar target/wenchang-brain-1.5.0-SNAPSHOT.jar --spring.profiles.active=mcp
```

- 产品：`http://localhost:8080/`
- MCP：`http://127.0.0.1:8091/mcp`
- MCP Health：`http://127.0.0.1:8091/actuator/health`

## 验证与管理 API

```powershell
mvn --% -Dmaven.repo.local=.m2/repository test
mvn --% -f extensions/wenchang-public-resource-mcp/pom.xml -Dmaven.repo.local=.m2/repository test
Invoke-RestMethod -Method Post http://localhost:8080/api/admin/reindex
Invoke-RestMethod -Method Post http://localhost:8080/api/admin/eval
Invoke-RestMethod -Method Post http://localhost:8080/api/admin/agent-eval
Invoke-RestMethod http://localhost:8080/api/knowledge/status
Invoke-RestMethod http://localhost:8080/api/agent/tools
```

主应用回归为 72/72 PASS；RAG Eval 为 25/25，Agent Eval 为 11/11。MCP 模块为 7/7 PASS，真实 Streamable HTTP `initialize / tools/list / tools/call` 发现并调用 7 个 Tool。浏览器实测覆盖 Command Bar、Agent/Skill 组合、Agent Detail、键盘选择、会话不提前创建、历史恢复与响应式布局；1920×1080、1440×900 与 390×844 均无水平溢出。

`data/wenchang-vector-store.json` 是可由 `knowledge/` 完整重建的运行期派生文件，不进入 Git。首次运行发现文件缺失或语料指纹变化时会自动重新索引，也可调用 `POST /api/admin/reindex` 主动生成。

## Development

Clone：

```bash
git clone git@github.com:FAIRY123456789/wenchang-brain.git
```

本地开发前将 `config/local-secrets.properties.example` 复制为被 Git 忽略的 `config/local-secrets.properties`，再自行填写 DeepSeek 与搜索 API 配置；不要提交真实密钥。

## 主要 API

- `GET /api/health`
- `GET /api/agents`
- `GET /api/skills`
- `GET /api/agent/tools`
- `GET /api/admin/diagnostics/agent`
- `GET /api/agent/runs?conversationId=...`
- `POST /api/agent/approvals/preview`
- `POST /api/agent/approvals/{id}/confirm|cancel`
- `GET /api/artifacts?conversationId=...`
- `GET /api/artifacts/{id}`
- `GET /api/artifacts/{id}/download`
- `DELETE /api/artifacts/{id}`
- `POST /api/chat`
- `POST /api/chat/stream`
- `GET/POST/PATCH/DELETE /api/conversations/**`
- `GET/PUT/POST /api/settings/model/**`
- `GET /api/knowledge/status`
- `POST /api/admin/reindex`
- `POST /api/admin/eval`
- `POST /api/admin/agent-eval`
- `POST /api/admin/knowledge/refresh-policies`

## 视觉资产

Hero 使用本地保存的文昌淇水湾—航天发射场实景：`src/main/resources/static/assets/wenchang-qishui-bay-launch-site.jpg`，Wikimedia Commons / Shujianyang / CC BY-SA 4.0。原创“海岸线 + 火箭轨迹”标志位于 `src/main/resources/static/assets/wenchang-logo.svg`。

## 对话版本与文件交互

- 用户问题支持原位编辑：点击问题右下角“编辑”，直接在保持蓝色对话气泡的编辑区修改；Enter 发送，Shift+Enter 换行，Esc 取消。
- 每次编辑创建一个可持久化的会话分支；问题旁显示 `1 / 2`、`2 / 2` 版本导航，刷新后仍可切换。
- 切换问题版本时，DeepSeek 会话记忆仅恢复当前分支的共享前缀和后续内容，不混入另一版本的回答；Artifact 也只从当前分支 Assistant Message 的持久化 metadata 恢复，不会把其他分支文件补挂到当前回答。
- 当前问题中的明确边界优先于 Agent/Skill 默认能力：例如“不要联网”会同时屏蔽确定性搜索路由和模型可见的联网工具，“不要生成文件”会屏蔽 Native/MCP Artifact 工具。
- Artifact 卡片只保留语义明确的“下载文件”。当前公网环境没有可靠的 DOCX 内嵌预览器，因此不再提供与下载行为重复的“打开”。
- 回答元信息只展示知识库、工具与来源，不再重复显示 `DeepSeek · deepseek-chat` 技术标签。

新增分支切换 API：`POST /api/conversations/{conversationId}/messages/{messageId}/activate`。