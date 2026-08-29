## 2026-08-29 · 1.5.5 单层原位编辑视觉修复

- 用户反馈：原位编辑态同时绘制外层蓝色消息气泡和内层白蓝色 textarea 边框，形成突兀的“双层输入框”，与正常对话气泡视觉不一致。
- Root Cause：`.message-content.inline-editing` 与 `.message-inline-editor` 分别设置了边框、圆角、背景和焦点阴影；textarea 还保留浏览器缩放柄，进一步强化了内层框感。
- 修复：外层气泡成为唯一编辑容器；textarea 改为透明、无边框、无圆角、无阴影且禁止手动缩放。键盘焦点通过 `:focus-within` 只反馈到外层气泡，保留可访问性与 Enter/Shift+Enter/Esc 行为。
- 本地验收：UI 契约测试 2/2 PASS；主应用 Maven 91/91 PASS；Secret assignment 扫描 0 命中。
- 发布：Release `1.5.5-single-surface-editor-20260829`，源码 `3f344a5`，归档 SHA-256 `471dfa8a…135e0fb`。隔离 canary 完成归档哈希、包内 checksums、Health 与单层 CSS 契约后停止。
- 正式验收：Main/MCP/Nginx active，NRestarts=0；正式 JAR 指向 V1.5.5。MCP `initialize / initialized / tools/list / tools/call` PASS；只读乡镇查询成功。
- 公网验收：`/wenchang-brain/`、CSS、Health 与既有 `/`、`/future-bay-eco-lab/` 全部 HTTP 200；公网 CSS 已确认旧内层背景不存在。
- 回滚：`/opt/wenchang-brain/backups/rollback-20260829T015318Z.properties`。生产 Key、日志与 Trace 均未读取或输出，未调用 DeepSeek/Tavily。
- 浏览器限制：桌面浏览器控制内核仍被 Windows sandbox helper 终止，未虚报可视化自动点击为 PASS；由 UI 契约、实际 JAR canary、正式静态资源与公网 HTTP 覆盖自动化验收。
## 2026-08-28 · 1.5.4 公网发布结果

- Release `1.5.4-edit-boundaries-20260828` 已从隔离 canary 切换至正式环境，源码 `d105bf9`，归档 SHA-256 `edad31c1…65951e`。
- Canary `127.0.0.1:18082` 使用内存 H2、独立 Artifact/Research/Trace、禁用 Search/MCP 且不加载生产 Secret；Health、蓝色编辑态、Enter/IME、无 `refreshConversationArtifacts`、Message Artifact 渲染均 PASS 后停止并清理临时目录。
- 正式 Main/MCP/Nginx 均 active，NRestarts=0；MCP `initialize / tools/list / tools/call` HTTP 200，协议 `2025-11-25`，工具数 7，龙楼镇只读查询返回成功。
- 公网产品、版本化 JS/CSS/i18n、Health 与既有 `/`、`/future-bay-eco-lab/` 七项全部 HTTP 200；实际 JAR 静态契约确认新版已生效。
- 回滚：`/opt/wenchang-brain/backups/rollback-20260828T093542Z.properties`。生产 Secret 未读取、未输出、未进入 Release；未调用 DeepSeek/Tavily。
- 浏览器限制：桌面浏览器控制内核连续两次被 Windows sandbox helper 终止，未把可视化自动点击标记为 PASS；由 UI 合同测试、ECS canary、正式 JAR 契约和公网 HTTP 覆盖自动化验收。
## 2026-08-28 · 1.5.4 编辑体验与任务边界修复

- 真实诊断：用户截图中的第 3 个问题版本实际 `toolNames=[]`、`assistantArtifactCount=0`，没有重新生成 Word；Conversation Artifact API 仍返回旧分支的 1 个文件。Root Cause 是前端在当前消息无文件时按 conversationId 拉取全会话 Artifact，并错误挂到最新 Assistant。
- 分支文件隔离：移除会话级 Artifact 回填。实时回答只接受本次 SSE `artifact_created/complete.artifacts`，历史恢复只读取当前活动分支 Message 的 `artifactsJson`；新增集成测试验证旧分支有 Word、新分支无 Word 时互不污染。
- 用户边界：新增本轮明确否定优先规则。“不要联网”屏蔽 Web/Official/Collect 工具，“不要生成文件”屏蔽 Word/Excel/CSV/政策简报/研学包；规则同时作用于确定性预执行、Skill Tool 集、无 Skill 模型 ToolCallback，以及带服务器前缀的 MCP 工具名。系统提示同步明确约束，禁止虚假声称已联网或已生成文件。
- 编辑体验：编辑区保持蓝色用户气泡视觉，宽度适度扩大；Enter 发送、Shift+Enter 换行、Esc 取消，并避开中文输入法合成态。五种界面语言的按钮统一改为“发送（Enter）”等对应文案。
- 验收：JavaScript 语法检查 PASS；针对性测试 12/12 PASS；主应用 Maven 91/91 PASS；MCP 非网络业务测试 6/6 PASS。本机真实 HTTP 用例受 Windows 沙箱 Java NIO loopback 限制，转由 ECS canary 执行。未调用 DeepSeek/Tavily，不消耗用户免费额度。
# PROJECT SUPERVISOR

## 2026-08-12 · Artifact 人类可读模板与回答下载链接

- Root Cause：通用 Word 路径把 MCP 包装 JSON 和原始用户指令直接拼入正文；标题仅机械删除少量动词，导致命令式长标题、无关记录与 JSON 协议内容。XLSX/CSV 也直接暴露英文字段名。
- 修复：新增 `ArtifactReportComposer`，递归解包 MCP 返回，按任务筛选记录、归纳专业标题、生成名单表格和核验说明，并只保留记录自身来源；Word Writer 增加统一标题层级、正文节奏、表格、来源说明与时间；XLSX 增加中文标题/表头、筛选、冻结窗格、链接和列宽；CSV 改为中文表头。
- 下载体验：Artifact 成功后，最终 Markdown 回答直接追加 `[下载 文件名](downloadUrl)`；文件卡片、SSE `artifact_created`、消息/Agent Run 持久化与刷新恢复继续保留。
- 回归：主应用 83/83 PASS，MCP 7/7 PASS；DOCX/XLSX 经 Apache POI 重新打开，CSV UTF-8 BOM 与中文表头通过。本机没有 LibreOffice/soffice，因此未执行 DOCX 页面 PNG 渲染，结构与内容验收通过。生产首轮样例进一步发现“高中”被泛化为“学校”且附属中学校名因含“大学”被误排除，现已改为“中学”定向检索并仅排除以大学/学院结尾的独立高校。

## 2026-08-12 · Artifact Download Closure

- Root Cause：DeepSeek 自主调用 Artifact MCP Tool 时可在 JSON 参数中生成语义化 `conversationId`；主应用只在 `ToolContext` 保存真实会话 UUID，远端 MCP 实际收到的仍是模型参数，因此文件写入错误会话目录。主应用随后按真实 UUID 查询为空，导致 Chat Response、SSE、Message 与前端文件卡全部缺失。
- 第二个实际问题：Spring AI MCP Callback 返回包装后的 content 数组而不是裸 Artifact JSON，首版采集器无法从嵌套文本取得 `artifactId`。修复为递归解析 MCP 包装层并按 ID 精确回查 manifest。
- 生产复测额外发现：权威上下文曾被注入所有 MCP Tool，查询型 `searchPublicServices` 的严格 schema 拒绝额外字段。现仅 4 个 Artifact Tool 覆盖 `conversationId / createdByAgent / skillId`；查询工具保持声明 schema。用户明确要求 Word/Excel/CSV 时，成果 Tool 进入确定性执行计划，不再依赖模型二次选择。
- 统一结构：新增 `ArtifactDescriptor`，覆盖 id、conversationId、type、filename、displayName、mimeType、sizeBytes、createdAt、downloadUrl、previewAvailable、sourceCount、createdByAgent、skillId。
- 传输与持久化：Chat Response 与 SSE `complete` 返回 `artifacts[]`；Message 保存 `artifactsJson`；AgentRun 保存 `artifactsJson`；AgentStep 保存 `artifactIdsJson`。若工具声称创建文件但 Registry 无 Artifact，系统会删除成功措辞并显示明确失败。
- 前端：回答下方显示 Word/Excel/CSV 文件卡、大小、来源数、打开和下载按钮；Agent Run 摘要显示文件数并列出成果；刷新时从 Message 与 Artifact API 恢复。所有 URL 经 `APP_BASE_PATH` 适配。
- 测试：主应用 80/80 PASS，MCP 7/7 PASS；本地 Word/XLSX/CSV 真实生成与下载通过，Word 可解包且中文/来源正常；390×844 无横向溢出。
- 生产：Release `1.5.0-artifact-fix2-20260812` 已部署到阿里云。真实任务调用 `searchPublicServices → createWenchangWordReport`，Artifact `6dabc2ff-ce51-44cf-9e4e-cb8626302f12` 写入真实会话 UUID 目录，公网下载 HTTP 200。`systemctl restart wenchang-brain` 后会话、Agent Run、Artifact 与下载仍恢复。

## 2026-08-10 · 第一轮从零建设

- 本轮目标：完成 Markdown → Document → Chunk → Embedding → SimpleVectorStore → RAG → ChatClient → Web UI → Source，并加入 WebSearchTool、Memory、Trace、Eval。
- 修改文件：初始化 Maven/Spring Boot 工程；新增 `knowledge/`、`src/main/`、`src/test/`、`README.md`、`docs/BAD_CASES.md`。
- 核心实现：Spring AI 2.0 `MarkdownDocumentReader`、`TokenTextSplitter`、`SimpleVectorStore`、`QuestionAnswerAdvisor`、`ChatClient`、`MessageWindowChatMemory`、`@Tool webSearch`；时效确定性路由；JSONL Trace；原生投屏聊天页。
- 知识摄取：5 个源文件；125 个 Markdown Document；118 个章节 Document；188 个 Chunk（4 / 29 / 35 / 47 / 73）；向量库已保存并可加载。
- 测试结果：`mvn test` 最终回归成功，4 项测试、0 失败、0 错误；固定 Eval 3/3 PASS（TEST 01：4 个来源、无工具；TEST 02：6 个来源、无工具；TEST 03：6 个来源并调用 `webSearch`）。实际联网搜索返回 6 条带链接的相关结果；Spring Boot、Health/Knowledge API、RAG 来源、Trace 均通过。
- 浏览器验收：桌面端首页、建议问题发送、回答渲染、来源展开均通过，页面控制台无错误；390×844 移动端无水平溢出，三张建议卡与输入区布局正常。
- 发现问题：受限沙箱禁止 Java NIO loopback；Bing RSS 中文查询失真；DuckDuckGo 后续出现超时/反爬；搜狗参数名曾配置错误；本地抽取回答曾误收系统提示。修复与回归记录在 `docs/BAD_CASES.md`。
- 当前状态：核心闭环完成，可在 `http://localhost:8080` 独立运行；本地无 Key 模式已实测。远程 OpenAI 兼容模式已配置，但当前环境没有模型凭据，远程 LLM/Embedding 尚未实测。

## 2026-08-11 · V1.1 运行时模型与体验迭代

- 本轮目标：在不破坏 V1.0 RAG / Tool / Trace / Eval 的前提下，加入页面运行时模型配置、DeepSeek 与 OpenAI 兼容模型切换、短期记忆、真实 SSE 流式响应、新会话重置和城市级首页视觉。
- 运行时模型：新增 `runtime/` 模块，用进程内 `RuntimeModelSettings` 保存密钥，以 `AtomicReference<ModelHandle>` 原子切换 `ChatClient`；请求启动时固定模型快照。默认仍为本地模型，Embedding 与 VectorStore 始终本地化，不受 ChatModel 切换影响。
- 设置 API：新增 GET / PUT / TEST / CLEAR 四个模型设置端点。GET 只返回是否已有密钥；TEST 返回真实连接结果与错误类型；清除后立刻回到本地模型。DeepSeek 明确支持 `deepseek-v4-flash`、`deepseek-v4-pro` 与思考模式字段，页面不展示推理过程。
- Agent：Memory 窗口扩展为 18 条消息；新增 Session Reset；同步与流式路径统一执行 RAG、时效路由、WebSearchTool、Memory 与 Trace。Trace 增加 modelMode/provider/model，继续做 Key 脱敏。
- Streaming：新增 `/api/chat/stream`，发送 `status / chunk / complete / error` SSE。远程模型真实消费 `ChatClient.stream().content()`；本地同步模型经 SSE 发送单一真实结果块。
- UI：重写 `index.html`、`styles.css`、`app.js`；采用 Core / Semantic / Component 三层 Token，加入文昌淇水湾—航天发射场宽幅实景、模型状态、设置抽屉、SSE 状态轨迹、响应式布局与可访问键盘交互。
- 主视觉：`static/assets/wenchang-qishui-bay-launch-site.jpg`，Wikimedia Commons / Shujianyang / CC BY-SA 4.0，2000×432；README 已写明作者、来源与许可。
- 测试：V1.0 4 项 + V1.1 5 项，共 9/9 PASS。新增覆盖密钥不泄漏、三轮 Memory 与 Reset、远程 RAG、时效 Tool/Trace、Streaming 内容一致性。
- 实际 API 验收：Health 为 V1.1 / LOCAL / RAG READY / 5 files / 188 chunks；缺 Key 测试返回 HTTP 422 与真实原因；配置状态从不返回 `apiKey`；同步 Chat、SSE 事件、来源、traceId 与 Session Reset 均通过。
- 浏览器验收：1920×1080、1440×900、390×844 均无水平溢出和控制台错误；首页、设置抽屉、缺 Key 反馈、SSE 回答、来源、新会话通过。移动端图标按钮补充固定可访问名称，关闭抽屉使用 `inert` 隔离焦点。
- 真实坏案例：Spring AI 未使用的音频自动配置无 Key 启动失败；本地同步模型误走 Stream API；Boot 4 测试夹具依赖假设；移动端隐藏文字导致按钮失去名称。修复与回归见 `docs/BAD_CASES.md`。
- 当前状态：V1.1 可独立启动并默认使用本地模式；远程模型构建、切换、脱敏和 Mock 调用已经回归通过。由于本轮未获得也未寻找用户 API Key，真实 DeepSeek 网络推理按要求留给用户在页面中完成唯一的人工动作。

## 2026-08-11 · V1.2

- 本轮目标：完成明亮 UI、左侧会话管理、H2/JPA 持久化、跨重启 Memory 恢复与服务端默认 DeepSeek 配置。
- 核心实现：新增 Conversation/Message Entity 与 Repository、会话 CRUD、首问标题、最近 18 条消息恢复；Chat/SSE 使用 conversationId 且按 USER → Agent → ASSISTANT 顺序落库。
- 模型配置：优先级为 Runtime Override > Server Default > Local；Health 与设置状态使用 REMOTE_RUNTIME / REMOTE_DEFAULT / LOCAL；GET 只返回脱敏 Key，并可恢复服务端默认值。
- UI：270 px 桌面侧栏、移动端抽屉、历史分组与重命名/删除、亮色实景 Hero、首问收束动画、大型 Composer、持久化消息与来源恢复。
- 数据位置：H2 文件库 `data/chat/wenchang-chat`；真实 `config/local-secrets.properties` 与数据库目录均已忽略。
- 测试：原有 9 项回归 + 2 项 Repository + TEST08–TEST12；浏览器按 1920×1080、1440×900、390×844 验收。
- 最终验收：`mvn clean package` 通过，累计 16 项测试全部通过；同步 Chat、SSE、会话 CRUD、标题重命名、密钥脱敏均通过真实 API 验收。服务重启后，同一会话 ID、标题与 6 条消息完整恢复，验证 H2 文件持久化和 Memory 重建有效。
- 浏览器验收：1920×1080、1440×900、390×844 均无水平溢出、错误浮层或控制台错误；桌面 270 px 侧栏、移动抽屉、设置抽屉、首发建会话、流式回答和重命名交互通过。验收产生的临时会话已清理。
- 当前状态：V1.2 已完成代码、测试、打包、真实 API/SSE、跨重启持久化与浏览器验收；服务以 `LOCAL` 模式运行于 `http://localhost:8080`，配置服务端 DeepSeek Key 后可切换为 `REMOTE_DEFAULT`。

## 2026-08-11 · V1.3 Agent、Tool、MCP 与知识工程迭代

- 本轮目标：完成统一 Tool Registry、Native Tool / MCP Tool 接入、确定性 Agent 路由、证据级 RAG、递归知识摄取、知识质量治理、25 题 Eval，以及四项 UI 首帧与交互修复。
- 最终架构：`Conversation API / SSE → CapabilityRouter → Tool Registry → ChatClient`；RAG 侧为 `Markdown Front Matter → ingestion supervisor → Document / section → Chunk → local embedding → SimpleVectorStore`，Trace 统一记录 conversation、model、retrieval、tool 与 source。
- 知识资产：20 个业务分类、30 个 active Markdown、62 个 Document / section / Chunk；实际入库 30 个唯一来源（P0=27、P1=0、P2=3），来源目录共 42 条（P0=37、P1=1、P2=4，含 12 条 supporting）；官方域名注册表 20 个、地点 16 个；旧 V1 资料保留在 `knowledge/_legacy` 并排除摄取。
- 摄取与持久化：递归遍历、下划线目录排除、Front Matter 校验、URL / 标题 / 内容哈希 / 近重复去重、P0→P1→P2→P3 优先级、相对路径与章节元数据传播；语料指纹匹配时直接加载 VectorStore，变更时原子重建。最终 `duplicatesSkipped=0`。
- Agent 与 Tool：Native Tool 为 `webSearch`、`officialSourceSearch`、`knowledgeEvidence`；MCP 通过 Spring AI 2.0 Tool Callback Provider 动态加入统一 Registry，默认关闭且无 MCP 服务也能启动；`/api/agent/tools` 返回真实 ToolDefinition 名称、描述和来源。
- 路由与证据：单请求只预取一种确定性能力，优先证据查询、其次官方来源、再次时效联网；预取工具从后续模型 ToolCallbacks 中移除，避免重复调用。RAG 增加分类元数据过滤与 `relative_path` 来源定位，修复 geography 检索混入 food 的真实噪声案例。
- UI：首屏从 `APP_LOADING` skeleton 原子 hydration 到 HOME / CHAT；仅 HOME 首问触发 Hero 收束；刷新和历史切换不触发大 Hero；桌面隐藏侧栏 X、移动端保留并管理 inert / focus；原创 SVG Logo 复用到侧栏、回答、favicon 和 loading；Composer 桌面 920 px、最小 122 px、17 px 字号，输入框内部 focus 无边框/阴影；模型名显示真实 `deepseek-chat`，历史消息恢复 Tool 标签。
- 测试：`mvn -Dmaven.repo.local=.m2/repository clean package` 最终 29/29 PASS、0 failure、0 error；RAG Eval 25/25 PASS；Tool Eval 3/3 PASS；Memory、Conversation、SSE、Trace、MCP 关闭启动、Native Tool 与静态 UI 合约全部通过。
- 实际 API / 性能：首次索引启动 13.13 秒；指纹匹配后的重启 12.44 秒并明确记录 `VectorStore loaded; corpus fingerprint matched`。本地实测 knowledgeEvidence 约 58 ms、webSearch 约 1.87 s、officialSourceSearch 约 4.85 s；远程模型未配置 Key，因此不报告虚假的远程首 Token 指标。
- 浏览器验收：1440×900 与 390×844 无水平溢出；首问动画、刷新/历史无动画、来源展开、模型设置、移动侧栏打开/关闭和焦点归还、持久化 Web / Official Tool 标签均通过；浏览器控制台 0 error / 0 warn；临时验收会话已清理。
- 当前状态：V1.3 JAR 为 `target/wenchang-brain-1.3.0-SNAPSHOT.jar`，服务以 LOCAL 模式持续运行于 `http://localhost:8080`；VectorStore READY，MCP 默认关闭，配置真实模型 Key 或 MCP Server 后可启用相应能力。

## 2026-08-11 · V1.4 Agent Experience + Deep Knowledge

- 目标：把 Agent 能力直接呈现在产品交互中，建立 `@Agent`、`/Skill`、公开 Agent Run、真实外部 MCP 服务，并系统扩充乡镇、公共资源、政策与地点知识。
- Agent Profile：建立 `wenchang / aerospace / ecology / study-tour / policy` 五个 Profile，统一由 `AgentProfileRegistry` 管理系统指令、知识类别、工具偏好、建议技能与输出结构。
- Skill：建立 `web-search / official-search / evidence-check / place-search / study-tour-plan / policy-search / deep-research / public-service` 八项可执行 Skill；深度研究公开展示 4 至 6 个任务步骤，研学规划使用真实坐标排序。
- 新增 Native Tool：`placeSearch`、`policySearch`；与 `webSearch / officialSourceSearch / knowledgeEvidence` 一起进入唯一 Tool Registry，返回结构化来源并写入统一 Trace。
- MCP Server：新增独立 Spring Boot 项目 `extensions/wenchang-public-resource-mcp`，以 WebMVC Streamable HTTP 运行于 `127.0.0.1:8091/mcp`，提供 `searchPublicServices / searchTownshipProfile / searchStudyTourPlaces`；主应用通过 Spring AI MCP Client 真实发现并调用，结果保留 `sourceId`。
- 知识扩展：active Markdown 从 30 增至 50，重新索引后为 136 Document / 136 Chunk；Sources Index 为 89 条唯一来源，P0/P1/P2=46/39/4，active/supporting=50/39，重复 ID 与 URL 均为 0。
- 乡镇数据：核验并建立文昌 17 个镇的结构化画像与 17 份独立 Markdown，覆盖概况、区位、产业、农业、生态、文化、公共服务、交通、研学与发展事项。
- 公共资源数据：`wenchang-public-services.json` 为 24 条，覆盖医疗、教育、文化、体育、政务、交通、科普和应急等类别；全部具有坐标、来源 URL 与 sourceId。
- 政策数据：`wenchang-policies.json` 为 10 条，统一保留组织、文号、日期、状态、分类、摘要与官方原始链接；刷新接口只发现官方候选，正式入库继续保留人工原文核验门槛。
- 地点修复：原空坐标的文昌航天超算中心、宋氏祖居、东郊椰林、铺前骑楼老街经多表达检索仍无法可靠交叉核验，已从 active 结构化地点中移除；最终 25 条地点的必填字段、坐标、sourceId 与适龄字段均完整。
- Eval：运行态 RAG Eval 25/25、Agent Eval 7/7；Tool Eval 覆盖 5 个 Native Tool；MCP `initialize / tools/list / tools/call` 及主应用 Agent 调用均通过，Trace 记录 `source=mcp`。
- 浏览器验收：桌面与移动视口验证 `@` Agent Selector、`/` Skill Palette、Chip、Agent Run、设置中的公共资源服务状态、侧栏与响应式布局；无密钥时显示“模型未配置”并引导设置。
- 当前状态：主应用版本 `1.4.0-SNAPSHOT`，正式构建 46/46 PASS；MCP 模块 4/4 PASS。当前环境未提供 `config/local-secrets.properties`，因此未把开发桩结果冒充 DeepSeek 实测；配置真实密钥后即可完成正式模型聊天、Tool Calling 与 SSE 验收。

## 2026-08-11 · V1.4 生产 Agent 能力验收与任务执行升级

- 真实 DeepSeek：运行时设置为 `REMOTE_RUNTIME / deepseek / deepseek-chat`，脱敏状态确认 Key 已配置；连接测试 HTTP 200，约 0.76 秒，最小 Chat HTTP 200。真实 Trace 同时确认 DeepSeek 自主调用 Native Tool 与 MCP `searchPublicServices`，后者返回文昌市图书馆及 sourceId。
- 搜索 Root Cause：`webSearch` 对搜狗请求得到 HTTP 302，Location 指向 antispider；根因分类为 `D / ANTI_BOT`，并连带造成依赖同一 Provider 的 `officialSourceSearch` 无动态结果。不是 DeepSeek、Router、Registry、MCP 或一般网络断连。
- Diagnostics：新增 `GET /api/admin/diagnostics/agent`，真实探测模型、隔离 `diagnosticEcho` Tool Calling、6 个 Native Tool、MCP 发现、SearchProvider、RAG 与 Artifact；设置页加入七项自检矩阵。
- Trace：统一为 `toolName / toolSource=NATIVE|MCP / stage / input / status / errorType / output / latencyMs`。失败文本和异常都标记 FAILED，普通页面不再把 Search 与 MCP 混为一谈。
- MCP：在原独立 8091 Server 上保留三项公共资源查询，并新增 `createWenchangWordReport / exportWenchangData / createStudyTourPackage / createPolicyBrief`。Word/XLSX 使用 Apache POI，CSV 使用 UTF-8，文件和 manifest 写入会话隔离目录。
- Artifact：新增 list/detail/download/delete API；路径经过 normalize、root 与 real path 防护；ChatResponse 与 Message 保存 Artifact metadata，回答与历史恢复均显示打开/下载卡片。
- Agent Profile：五个 Profile 补齐任务、输入、工具、输出、审批、流程、示例与完成判据；侧栏点击先显示 Detail Panel，`@Agent` 选择后显示可重新打开的 Context Card。
- Task Skill：扩为十三项并按研究、工作成果、数据与地点分组；深度研究、政策简报、研学方案和数据导出绑定真实生产 Tool 与 Artifact 输出。
- Agent Run：新增独立 `AgentRun / AgentStep` JPA 持久化与查询 API，步骤保存 toolSource、状态、耗时、摘要与错误；消息继续保存完成态快照，页面刷新可恢复。
- Human-in-the-loop：新增 `AgentApproval` 与 preview/confirm/cancel API；重新索引、政策知识批量刷新、加入知识库和删除数据请求触发 `approval_required`，文件生成则由用户原请求直接确认。
- Markdown：根因是 SSE chunk、complete 和历史消息全部写入 `textContent`。现统一使用本地 marked 15.0.12 + DOMPurify 3.2.6；raw buffer debounce、最终和历史共用 Renderer，支持标题、列表、链接、代码和表格。
- Harness：生产 Agent Eval 新增 `AGENT-POLICY-01 / AGENT-STUDY-01 / AGENT-DATA-01 / AGENT-RESEARCH-01`，检查工具、来源、Agent Run、Artifact 类型与文件可打开；同时新增 Artifact API、Trace、Diagnostics、Search、Markdown DOM、HITL 与持久化契约。
- 最终验收：主应用 Maven 58/58 PASS，RAG Eval 25/25，生产 Agent Eval 11/11，MCP 模块 7/7；8091 `initialize / tools/list / tools/call` 七工具全通过，DeepSeek→MCP 实际命中 `searchPublicServices`。Word、XLSX、CSV 均真实生成并重新打开，Artifact 列表/详情/下载为 HTTP 200。
- 浏览器：桌面首页、政策助手详情、Agent Context Card、分组 Skill Palette、七项诊断、Markdown 历史 DOM 和 Agent Run 恢复均通过，控制台 0 error；当前 in-app browser 的移动视口 override 未生效，390×844 由已有静态响应式合约覆盖，未冒充为本轮真实移动截图。
- 当前运行态：8080 与 8091 均 UP，RAG 50 文件 / 136 chunks，MCP 7 工具已连接。DeepSeek Key 原仅存于旧进程 Runtime Override，安全重启后需用户在设置页重新输入；重启前的真实 DeepSeek Chat、Native Tool Calling 与 MCP Tool Calling 证据已留存，未读取或落盘密钥明文。
- 文件可见验收追加修复：Word 已由本机 Microsoft Word 真实打开，中文、A4 两页、标题、项目符号正常。同时发现并修复“初二”被主应用降级为“全年龄”的参数错位；最终研学 Word 包含 8 来源，增强来源断言后 Agent Eval 仍为 11/11。

## 2026-08-11 · V1.5 · Agent Command Experience Finalization

- 交互定位：智能体与技能统一进入 Composer 上方 Command Bar。默认仅显示 `@ 选择智能体` 与 `/ 使用技能`，选择器在原位展开，不跳转页面、不发送请求、不提前创建会话。
- Agent：五个名称统一为 Wenchang Assistant、Aerospace Researcher、Ecology Researcher、Study Tour Planner、Policy Assistant；界面保留中文定位、能力、输入、流程、人工确认、示例与完成判据，不再展示 Agent 图标或侧栏 Agent 列表。
- Skill：十三项生产技能按“研究与检索 / 任务与文件 / 规划与服务”分组；Agent 与 Skill 独立保存并允许组合，真实请求继续提交 `agentId / skillId`。
- 状态机：实现 `COMMAND_IDLE / AGENT_SELECTING / SKILL_SELECTING / AGENT_SELECTED / SKILL_SELECTED / AGENT_AND_SKILL_SELECTED / DETAIL_OPEN`，支持点击、输入框 `@`/`/` 触发、方向键、Enter、Tab、Esc、切换、移除与焦点归还。
- Detail：详情面板改为文字优先的大字号布局；桌面宽面板、移动端全屏，原生 dialog 维持焦点约束。“开始使用”只回到当前 Composer 并保留选中 Agent。
- 会话与任务：只有发送消息才创建 Conversation；新会话恢复默认 Agent、无 Skill；历史会话继续恢复 Agent、Skill、Agent Run、来源与 Artifact 卡片。
- API：`GET /api/agents/{id}` 与 `GET /api/skills/{id}` 返回 V1.5 详情元数据；列表接口保持兼容。
- 回归：主应用 Maven 62/62 PASS；RAG 50 文件 / 136 chunks，既有 Agent Eval、Artifact、AgentRun、Approval 与 Markdown 契约均保持通过。
- 浏览器：1920×1080、1440×900 与 390×844 均无水平溢出；桌面 Agent/Skill 三列、移动两列；实测 Agent+Skill 组合、详情关闭/开始使用、键盘 Enter/Tab、零空会话创建和移动端全屏详情均通过。
- 运行约束：按用户要求未重启或终止当前 8080/8091。V1.5 使用独立 8082 临时实例完成浏览器验收，验收后关闭；当前 8080 将在下一次有计划发布时加载 V1.5 JAR。

## 2026-08-12 · 文昌智脑 V1 · 阿里云正式部署

- 本地 Release：`1.5.0-SNAPSHOT` 已重新 clean package；主应用 72/72、MCP 7/7，均为 0 failure / 0 error。部署阶段冻结核心业务，只补充 `/wenchang-brain/` Base Path 契约与部署资产。
- LOCAL_RELEASE_CANDIDATE：version=`1.5.0-SNAPSHOT`；main jar 与 MCP jar 已生成；DeepSeek 真实连接与 Tool Calling 为 PASS；Tavily 在同一配置环境中已用一次必要查询取得真实网页结果；RAG=50 files / 136 chunks；MCP initialize/list/call=PASS；Conversation/Memory、Artifact Word/XLSX/CSV 和下载契约均为 PASS。部署调整只涉及前端 Base Path 与部署文件，未再次消耗搜索额度。
- 发布结构：主应用与 MCP 仅上传可执行 JAR；Knowledge 和必要 Data 作为 seed；不上传 `.git`、`.m2`、本地 H2、Artifact、Research、日志或真实 Secrets。当前仓库尚无 Git commit，发布信息不得伪造 commit。
- 目录与隔离：规划 `/opt/wenchang-brain/{app,mcp,config,data,knowledge,logs,runtime,releases,backups}`，独立用户 `wenchang`，服务使用明确 Java 17 路径，不修改服务器默认 Java、Python 或 Node。
- systemd：规划 `wenchang-mcp` 为 `127.0.0.1:18091`、主应用为 `127.0.0.1:18080`，有限 JVM Heap、失败重启、文件系统写权限白名单；H2、VectorStore、Artifact 与 Research 均位于持久目录而非 Release。
- Nginx：规划沿用现有站点 server block，仅新增 `/wenchang-brain/` 反代；SSE 关闭 buffering/cache。变更前备份 owner 配置，只有 `nginx -t` 成功才 reload，并回归已有首页及 `/future-bay-eco-lab/`。
- Secret Strategy：服务器独立文件 `/opt/wenchang-brain/config/local-secrets.properties`，权限 `0600`；Release、JAR、Git、文档和 Trace 均不包含真实 DeepSeek、Tavily 或 Brave Key。
- SSH 状态：`120.26.238.159:22` 网络与 SSH 服务可达，但本机批处理认证返回 `Permission denied`，未找到可用私钥。遵循停止原则，尚未执行服务器侦察、安装、Nginx 修改或公网验收。
- 当前状态：`DEPLOYMENT_INPUT_REQUIRED`。唯一缺少项为 SSH Key Path；获得后从只读远程预检继续。公网地址尚未宣称上线，详见 `docs/deployment/ALIYUN_DEPLOYMENT.md`。
- 远程预检更新：用户提供私钥后已确认目标主机为 Alibaba Cloud Linux 3.2104 U13.2 x86_64；MemAvailable 约 1.49 GiB、无 Swap、磁盘可用约 18 GiB，18080/18091 空闲，DeepSeek/Tavily 出口可达。现有首页与 `/future-bay-eco-lab/` 均为 HTTP 200，未作修改。服务器无 Java，仓库提供 OpenJDK 17.0.20；将以明确绝对路径运行，并按无 Swap 环境将主/MCP 限制为 `MemoryMax=560M/320M`。
- 真实发布问题：Windows `Compress-Archive` 生成的 ZIP 在 Linux 解压时损坏 5 个中文 legacy 文件名，归档总哈希虽一致但逐文件哈希失败；部署被主动拦截，未安装应用。发布格式已改为 UTF-8 `tar.gz` 并要求服务器逐文件 SHA-256 全通过。
- 基础上线：OpenJDK 17.0.20 已安装；MCP/Main systemd 均 enabled、active，分别仅监听 127.0.0.1:18091/18080。MCP 7 tools，RAG 50 files / 136 chunks，VectorStore fingerprint matched，H2 位于持久目录。Nginx owner 配置已备份并在 `nginx -t` 成功后仅 reload；公网静态资源/API 与既有首页、`/future-bay-eco-lab/` 均为 HTTP 200。
- Secret Gate：服务器 `/opt/wenchang-brain/config/local-secrets.properties` 权限/owner/加载参数正确，但 DeepSeek、Tavily、Brave 的 valueLength 均为 0；本机配置不会自动复制。当前暂停真实 Agent/Tavily/Artifact 终验，状态为 `SERVER_SECRETS_REQUIRED`。
- DEPLOYED_RELEASE：`1.5.0-rc1-20260812` 已正式上线至 `http://120.26.238.159/wenchang-brain/`。服务器 Secrets 由用户使用 `vim` 填写，权限 `0600`；后续禁止 nano，检查不读取 Key、日志或 Trace 明文。
- 生产模型与搜索：DeepSeek `REMOTE_DEFAULT / deepseek-chat`，真实 diagnosticEcho Tool Calling=PASS（约 7.15s）；Tavily=AVAILABLE（约 1.65s）；官方检索得到 6 条白名单结果。Brave 未配置且属于可选 fallback。
- MCP：独立 Streamable HTTP initialize=200、protocol=2025-11-25；tools/list=200、7 tools；tools/call searchTownshipProfile=200、isError=false。DeepSeek Agent 真实调用 MCP searchPublicServices，`toolSource=MCP`、completed。
- Agent/Artifact：政策助手任务完成 7 steps，调用 5 工具并生成 9 来源 Word；Word 公网下载 HTTP 200、真实 reopen、72 段、中文/来源 PASS。地点数据导出 XLSX 公网下载 HTTP 200、26 行/9 列、中文/URL PASS。
- 持久化与流式：Conversation 在主应用和 MCP 顺序重启后仍保留 2 条消息、Agent、Skill、来源与 AgentRun。公网 SSE 101 个 answer_chunk、complete=true、error=false；Markdown 历史 strong DOM、Command Bar、Artifact 卡片、静态资源与 Base Path 正常。
- 资源与回归：最终 MCP/Main 当前启动周期 0 error、0 restart；内存约 179 MiB / 345 MiB，MemAvailable 约 918 MiB，磁盘可用约 17 GiB。Nginx `-t` PASS；现有 `/` 与 `/future-bay-eco-lab/` 均保持 HTTP 200；页面应用 Console 0 error。
- 发布证据：main SHA-256=`3344be68f74772128cf6f4127866d6d5655cf21aa39a863218c9462f9194783c`；MCP SHA-256=`29663181134c262d9416ee783aa235f78e9989836fa2361a00cb57163a63c926`；corpusSignature=`23194410bf6a1a3c9869edadb773d92d47188257c91f296ca08af3689f0ddf75`；Git=`UNCOMMITTED`。

## 2026-08-12 · Git Repository Baseline

- Git 状态：已有本地仓库但此前无 Commit；未伪造历史，当前完整稳定工程作为首个正式基线。
- Baseline Commit：`feat: establish Wenchang Brain V1 stable baseline`；Commit SHA 以本文件所在 Git Commit（即发布后的 `origin/main`）为准，最终值同时记录在发布验收结果中。
- GitHub Repository：`git@github.com:FAIRY123456789/wenchang-brain.git`；主分支统一为 `main`。
- 测试：主应用 Maven 72/72 PASS，MCP 模块 7/7 PASS，均为 0 failure / 0 error。
- Secret 清理：本地 Secret、部署私有配置、SSH Key、日志、H2、Artifact、Research、VectorStore、Maven Cache 与构建产物均由 `.gitignore` 排除；Example 配置只保留空字段结构。
- 首次 Push：完成后以 `origin/main` 与本地 `HEAD` 相同且 working tree clean 为验收标准；未操作已部署服务器。

## 2026-08-21 · 五语系统界面与生产同步

- 产品范围：只扩展系统界面语言，不改变 DeepSeek、Agent、Skill、MCP、RAG、Search、Artifact 或 Conversation 的业务协议和模型请求语言。
- 语言能力：设置抽屉支持中文（默认）、English、Bahasa Indonesia、العربية、Português；语言选择保存在 `localStorage`，刷新恢复；阿拉伯语设置 `html[dir=rtl]`，其他语言为 LTR。
- 前端架构：新增唯一 `WenchangI18n` 运行时与 `data-i18n` 契约，静态 HTML、Command Bar、设置、Agent/Skill 状态和动态标签统一使用同一翻译入口；语言偏好不写入服务器、不进入模型 Prompt。
- 本地验收：`node --check i18n.js`、`node --check app.js` PASS；Maven clean package 85/85 PASS，MCP 7/7 PASS；新增系统语言 UI 合约并保持既有 Agent Command Experience 回归通过。
- Release：`1.5.0-i18n-20260821`，源码 Commit=`3e35f5f47b3357fd01caf1f06416d5f0f0dd61a1`，归档 SHA-256=`9487f61f66daece4e472c9fe37a4a5e4a47a76bfc81edadcf9d50292b7e55150`；敏感/运行态文件扫描为 0 项。
- 预发布：在 ECS 独立目录与 `127.0.0.1:18082` 启动隔离实例，未加载生产 Secrets，未连接 MCP 或 Search；Health、Agent、Skill、五语 HTML/JS 标记、RTL 逻辑和 JS/CSS MIME 通过后关闭，正式服务始终 active。
- 正式切换：应用与 MCP symlink 指向 `/opt/wenchang-brain/releases/1.5.0-i18n-20260821/`；主应用、MCP、Nginx 均 active，主/MCP `NRestarts=0`，18080/18091 继续仅监听 loopback。
- 生产健康：DeepSeek=`REMOTE_DEFAULT / deepseek-chat`，RAG=`READY`，VectorStore=`LOADED`，50 files / 136 chunks；本轮不额外消耗 DeepSeek 或 Tavily 额度，因为语言功能不改变服务端 AI 链路。
- 公网验收：`/wenchang-brain/`、五语脚本、主脚本、样式、Health、Agent、Skill、Conversation 均 HTTP 200；既有 `/` 和 `/future-bay-eco-lab/` 均保持 HTTP 200；Nginx `-t` PASS。
- Secret 与回滚：生产 Secret 文件保持 `0600 wenchang:wenchang` 且未输出内容；预切换回滚点为 `/opt/wenchang-brain/backups/predeploy-i18n-20260821T080553Z.properties`，部署回滚元数据为 `/opt/wenchang-brain/backups/rollback-20260821T080553Z.properties`。

## 2026-08-21 · 公网语言切换可靠性与设置 UI 修复

- 真实 Root Cause：旧版 `setLanguage()` 尝试写入 `localStorage` 后，`apply()` 又通过 `getLanguage()` 重新读取存储。在隐私模式、内嵌浏览器或受限公网环境中，存储写入异常被安全捕获，但二次读取回到默认 `zh-CN`，造成用户点击语言后界面看似没有变化。
- 运行时修复：新增进程内 `activeLanguage` 作为当前语言唯一事实源；切换时先更新内存状态，再尽力持久化，最后统一渲染并发出 `wenchang:languagechange`。即使本地存储读写均不可用，当前页面仍立即切换；存储可用时继续支持刷新恢复。
- UI 修复：原生下拉框升级为与系统一致的玻璃质感语言卡片，五种语言采用可访问 radio button 语义、选中勾选、Hover/Focus 状态、移动端双列布局，并继续保留隐藏原生 select 作为兼容契约；阿拉伯语 RTL 同步覆盖按钮布局。
- 缓存修复：`styles.css`、`i18n.js`、`app.js` 使用 `v=1.5.1-language-ui` 版本查询参数，确保公网部署后不继续命中旧静态资源。
- 本地验收：Node 语法 PASS；模拟 `localStorage.getItem/setItem` 同时抛错时，English、العربية、Português 切换、RTL、选中状态、事件和动态文案全部 PASS；Maven clean package 85/85 PASS，且测试显式清空模型 Key，未消耗 DeepSeek/Tavily 额度。
- Release：`1.5.1-language-ui-fix-20260821`，Git=`0dc30e831830cc548d42aee29c0bd14fa78855cc`，归档 SHA-256=`822a435f56b5e50a001bd4ea9762e995a367624142993a8460ad3bb1a1c23540`。
- 生产验收：隔离 canary `127.0.0.1:18082` 在无 Secret、无 MCP、无 Search 状态下 Health 与版本化 HTML/JS/CSS PASS，验收后关闭；正式 symlink 已切换至新 Release，Main/MCP/Nginx 均 active，NRestarts=0，18080/18091 继续仅监听 loopback。
- 公网状态：`http://120.26.238.159/wenchang-brain/` 返回新版语言控件及版本化资源 HTTP 200；DeepSeek=`REMOTE_DEFAULT/deepseek-chat`，RAG=50 files/136 chunks，MCP Health=UP；已有 `/` 与 `/future-bay-eco-lab/` 仍为 HTTP 200。
- 回滚：`/opt/wenchang-brain/backups/rollback-20260821T121238Z.properties`。生产 Secret 内容未读取、未输出，权限策略未改变。
- 浏览器说明：桌面应用的可视化浏览器控制连接被本机沙箱组件错误阻断，本轮未把自动点击冒充为 PASS；以无存储运行时测试、隔离 canary 和公网静态资源实测作为自动化证据，并保留用户端最终视觉确认入口。
## 2026-08-28 · 联网自检状态契约与用户消息操作

- 真实 Root Cause：SearchProviderHealth 的 JSON 状态字段名为 health，而前端 diagnosticAvailable() 只读取 available / connected / ready / status。因此搜索真实返回 health=AVAILABLE 时仍会被页面误判为“异常”；这是诊断展示契约错配，不是 Tavily 联网能力本身失败。
- 契约修复：后端为 available() 显式输出 JSON 布尔字段 available；前端同时兼容 available / health / status，保留对旧响应的向后兼容。服务重启后 /api/health 的搜索状态为 UNKNOWN / NOT_CHECKED，不会把“尚未检查”当作历史故障。
- 消息操作：每条用户问题右下角新增“复制”和“编辑”。复制优先使用 Clipboard API，并在公网 HTTP 环境下回退到用户手势内的兼容复制；编辑会把原问题载入 Composer、聚焦并移动光标到末尾，等待用户确认后重新发送，不删除或篡改历史消息。
- 国际化与 UI：五种系统语言均补齐操作文案；按钮为轻量图标文本样式，支持 Hover、键盘焦点、完成反馈、移动端和 RTL。
- 本地验收：JavaScript 语法、Git diff 检查、诊断 JSON 序列化契约和消息 UI 契约均 PASS；Maven clean package 85/85 PASS，未调用真实 DeepSeek/Tavily。
- Release：1.5.2-diagnostics-actions-20260828，源码 Commit=45447d5610960a6fbe6f60ee86269d7b2f78bb0e，归档 SHA-256=10be8e37474027cc8c3193aac541452724006b15e874da505f1c9997c0e1c1c3。
- 灰度与生产：隔离 canary 127.0.0.1:18082 在无 Secret、无 MCP、无 Search 状态下验证 health + available 双字段、新版 HTML/JS/CSS 和消息操作资源后关闭；正式 Main/MCP/Nginx 均 active，NRestarts=0，公网版本资源和既有两个站点均 HTTP 200。
- 回滚：/opt/wenchang-brain/backups/rollback-20260828T005816Z.properties。生产 Secret 未读取、未输出、未进入 Release。
- 浏览器限制：桌面应用的浏览器自动控制连接连续两次被本机运行组件终止，未把自动点击冒充为 PASS；公网静态资源、隔离运行时、后端序列化和 UI 合同测试构成自动化验收证据。
## 2026-08-28 · 文件操作与持久化对话分支

- Artifact 交互：移除实际行为与下载完全相同的“打开”入口；在没有可靠 DOCX 内嵌预览器的当前架构下，统一保留一个明确的“下载文件”按钮。
- 回答元信息：移除普通用户无须关注的 `DeepSeek · deepseek-chat` 灰色标签，保留知识库、工具与来源等可解释信息。
- 原位编辑：用户问题在当前消息气泡内进入编辑态，取消或发送均不跳到页面底部 Composer；支持 Esc 取消与 Ctrl/Cmd+Enter 发送新版本。
- 会话分支：Message 新增 parentMessageId、revisionGroupId、revisionIndex，Conversation 保存 activeLeafMessageId。编辑产生同级问题版本，旧问题、旧回答及其后续消息完整保留。
- 版本导航：用户问题右下角显示上一版、`当前 / 总数`、下一版；切换版本后服务端返回该问题子树的最新活动路径，页面刷新仍保持选择。
- 记忆隔离：ChatMemory 仅从 active leaf 反向恢复当前分支，编辑较早问题时只保留其共享前缀，防止旧分支回答污染新请求。
- 兼容迁移：旧线性 Conversation 在首次读取时自动补齐父子关系和问题版本标识；Hibernate update 为现有 H2 增加可空字段，不删除历史消息。
- 本地验收：分支持久化与记忆隔离集成测试 2/2 PASS；消息 UI、五语、子路径与请求契约定向测试 8/8 PASS；Maven clean package 87/87 PASS；未调用 DeepSeek/Tavily。
## 2026-08-28 · 1.5.3 公网发布结果

- Release `1.5.3-conversation-branches-20260828` 已从隔离 canary 切换至正式环境，源码 `2e36b48`，归档 SHA-256 `b08ed244…b69600`。
- 生产 Main/MCP/Nginx 均 active，NRestarts=0；Secret 权限与属主保持 `0600 wenchang:wenchang`，未读取内容。
- 旧 H2 经 Hibernate 增量迁移后仍可读取 15 个 Conversation；新字段只用于消息父链、问题版本与活动分支，没有删除历史消息。
- 公网七项 URL 全部 HTTP 200；新版 JS 确认原位编辑与 `/activate` 分支接口存在，重复“打开”和模型灰字渲染不存在。
- 回滚元数据：`/opt/wenchang-brain/backups/rollback-20260828T060901Z.properties`。
- 浏览器自动控制因本机 Windows 沙箱 helper 故障未能建立，未标记可视化自动点击 PASS；全量测试、canary 与公网契约均已通过。