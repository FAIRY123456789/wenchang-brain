# Bad Cases

这里只记录本轮真实启动和回归过程中出现过的问题。

## 32. Artifact 把 MCP JSON 和原始指令写进 Word（2026-08-12）

- 现象：Word 标题接近原始提示词，正文出现 `searchPublicServices` 与双重转义 JSON；小学、学院等无关记录混入“高中名单”，来源列表还出现与名单无关的地理编码查询链接。
- Root Cause：通用报告参数使用 `augmentWithToolResults()`，把整个工具协议结果作为正文；标题只做关键词删除；来源退回 RAG 来源而非名单记录自身来源。
- 修复：新增结构化 Artifact Composer，递归解包 MCP wrapper，按任务条件筛选行、生成专业标题和人类可读 Markdown 表格，并以最终记录的 `sourceOrganization/sourceUrl` 构造来源。Word Writer 再加协议 JSON 防漏过滤。
- 验证：包装 JSON 测试能提取文昌中学并排除小学/学院；Word 中无 `tool/results` JSON，存在表格、来源与核验提示。
- 生产补充：首轮真实任务只命中文昌华侨中学。原因是资源关键词先匹配到泛化“学校”，同时过滤规则把“清华大学附属中学文昌学校”误判为高校。现将高中/初中请求优先改写为“中学”，并仅排除名称以“大学/学院”结尾的独立高校。

## 33. 生成文件只能点卡片，回答正文没有下载链接（2026-08-12）

- 现象：文件卡片可以下载，但模型回答正文没有可点击链接，不符合用户阅读习惯。
- 修复：主应用取得真实 ArtifactDescriptor 后，在最终 Markdown 追加 `/wenchang-brain/api/artifacts/{id}/download` 超链接；MarkdownRenderer 统一渲染为安全的新窗口链接。
- 验证：链接结构契约通过，并保留 Artifact 卡片作为第二入口。

## 1. 受限 Windows 环境中 JDK HttpClient 阻断应用启动

- 问题：首次启动在创建 `WebSearchTool` 时失败。
- 实际行为：`HttpClient.newBuilder().build()` 初始化 NIO selector，受限环境无法建立内部 loopback，Spring Bean 创建失败。
- 预期行为：即使暂时无法访问外网，应用本身也应能启动，Tool 在调用时返回清晰错误。
- 原因：JDK `HttpClient` 初始化依赖 selector/loopback，当前沙箱禁止该行为。
- 修复：搜索请求改用同步 `HttpURLConnection`，仅在 Tool 被调用时建立外部连接。
- 回归：PASS。Bean 可创建；应用在允许 localhost 的环境中成功启动，`/api/health` 返回 `UP`。

## 2. 长中文问句在 Bing RSS 中只按首字检索

- 问题：询问“近期文昌航天发射”确实触发 Web Search，但结果是“请”字的词典页面。
- 实际行为：Trace 显示工具已执行，但 Bing RSS 对长中文标点问句的查询解析失真；缩短中英文查询仍返回无关结果。切换搜狗后的第一次回归仍为空，因为沿用了 `q` 参数，而搜狗使用 `query`。
- 预期行为：返回文昌航天发射、任务和观礼信息。
- 原因：当前网络出口下 Bing RSS 查询质量/编码行为不稳定，不能只用“是否调用 Tool”作为成功标准。
- 修复：增加领域搜索词重写。DuckDuckGo HTML 后续出现连接超时/Lite 机器人挑战，最终切换到当前环境可稳定访问的搜狗 HTML，按站点选择 `query` 参数并解析标题、链接与摘要。
- 回归：PASS。相同查询可返回中国载人航天官方网站、2026 文昌火箭发射计划等结果；Tool Trace 保留实际输出与耗时。

## 3. 本地演示模型误抽取系统提示词

- 问题：普通 RAG 能返回来源，但本地抽取式回答包含“你是文昌智脑”等系统指令。
- 实际行为：本地模型从完整 Prompt 的前几行抽取，系统消息排在 RAG Context 前。
- 预期行为：只展示检索资料和联网结果。
- 原因：没有先定位 `QuestionAnswerAdvisor` 的 Context 分隔标记。
- 修复：仅抽取 `Context information is below` 后的知识上下文，并在存在时合并“联网搜索结果”区段。
- 回归：PASS。回答不再复述系统指令。

## 4. 关闭 Chat/Embedding 后仍自动创建 OpenAI 语音模型

- 问题：V1.1 测试与编译通过，但打包 JAR 在没有 API Key 时启动失败。
- 实际行为：`spring.ai.model.chat=none` 与 `embedding=none` 已生效，Spring AI 2.0 的 `OpenAiAudioSpeechAutoConfiguration` 仍因 `matchIfMissing=true` 尝试创建语音模型，最终报“至少需要一种 credential source”。
- 预期行为：应用未配置任何远程密钥也能完整启动；远程 ChatModel 只能由页面设置显式创建。
- 原因：OpenAI Starter 同时携带 Chat、Embedding、Image、Moderation、Speech 和 Transcription 自动配置，各模型类型的开关彼此独立。
- 修复：在 `application.yml` 中把所有未使用模型类型显式设为 `none`；运行时 ChatModel 继续由 `SpringAiOpenAiModelFactory` 直接构建。
- 回归：PASS。打包 JAR 无 Key 启动，Health 显示 LOCAL，RAG 载入 5 个文件与 188 个 Chunk。

## 5. 本地同步 ChatModel 被直接送入 Streaming API

- 问题：SSE 端点能连接，但本地模式最终返回“streaming is not supported”的失败回答。
- 实际行为：`ChatClient.stream()` 调用了 `LocalDemoChatModel` 继承的默认 `stream()`，该默认方法抛出 `UnsupportedOperationException`。
- 预期行为：远程模型逐块流式输出；本地模型也应通过同一 SSE 协议返回真实结果，且不能伪造逐字动画。
- 原因：编排层把“HTTP 使用 SSE”错误等同于“底层模型一定实现 Reactor Flux”。
- 修复：REMOTE 模式真实消费 `ChatClient.stream().content()`；LOCAL 模式同步调用后发送一个完整 `chunk`。只有异常链明确包含 `UnsupportedOperationException` 时才允许兼容性同步降级，其他远程错误继续显式失败。
- 回归：PASS。真实 HTTP 依次收到 retrieval、retrieved(6)、generation、chunk、completed、complete；完整回答、4 个来源与 traceId 正常。

## 6. Spring Boot 4 测试夹具沿用旧版 MockMvc 假设

- 问题：新增设置 API 测试最初无法编译，改为无 Web 环境后又因 `ObjectMapper` Bean 不存在而无法注入。
- 实际行为：当前 Boot 4 测试依赖未暴露旧路径下的 `AutoConfigureMockMvc`；`webEnvironment=NONE` 也不会保证注册应用未声明的 `ObjectMapper` Bean。
- 预期行为：测试应稳定验证 DTO 脱敏、运行时切换、Memory、RAG、Tool、Trace 与 Stream，不依赖无关 Web 测试自动装配。
- 原因：测试代码沿用了旧版 Starter 的自动配置边界。
- 修复：服务与控制器契约采用直接集成测试，测试内显式创建并注册模块的 `ObjectMapper`；HTTP 状态和 SSE 另由真实启动实例验收。
- 回归：PASS。V1.0 + V1.1 共 9 项测试、0 失败、0 错误。

## 7. 移动端隐藏按钮文字后失去可访问名称

- 问题：390 px 视口把“新会话 / 模型设置”文字设为 `display:none` 后，浏览器按可访问名称无法定位两个图标按钮；关闭的抽屉也仍存在潜在键盘焦点。
- 实际行为：视觉布局正确，但小屏读屏和纯键盘用户无法可靠辨认操作；隐藏抽屉的输入控件可能进入 Tab 顺序。
- 预期行为：桌面与移动端共享稳定按钮名称，关闭抽屉完全退出交互树。
- 原因：可访问名称依赖了会在响应式样式中隐藏的可见文字；抽屉只设置 `aria-hidden` 与位移，没有隔离焦点。
- 修复：两个图标按钮增加固定 `aria-label`；抽屉关闭态增加 `inert`，打开与关闭时同步切换。
- 回归：PASS。移动端抽屉宽度与视口一致，无水平溢出，图标按钮保留名称，Escape 关闭有效。

## 8. 历史会话刷新先出现大 Hero

- 问题：已有消息的会话刷新时，首帧先显示 420 px Hero，再在接口完成后缩成 132 px。
- 原因：静态 HTML 默认是 HOME，`initialize()` 又等待多个状态请求，Hero transition 始终开启。
- 修复：HTML 首帧直接声明 `APP_LOADING` 并显示极简 loading；只用会话列表和 active detail 决定 HOME/CHAT，一次性完成 hydration。Hero transition 仅在 hydrated HOME 发送首问时临时开启。
- 回归：PASS。刷新、active conversation 恢复和历史切换直接进入 Compact Hero；首次可见帧不播放收缩动画。

## 9. Sidebar 桌面叉号与 Composer 内部矩形同时出现

- 问题：桌面 Sidebar Logo 旁出现用途不明的 `×`；textarea 聚焦时外层圆角之外又出现矩形阴影。
- 原因：`.icon-button` 的同优先级规则覆盖 `.sidebar-close`；全局 `textarea:focus-visible` 又覆盖局部 `outline: 0`。
- 修复：桌面使用 `.sidebar .sidebar-close { display:none }`，只在移动媒体查询显示；textarea 的默认、focus、focus-visible、active 四种状态全部清除 border/outline/box-shadow，只保留 `.composer:focus-within`。
- 回归：PASS。三视口桌面/移动规则与 3 项 UI 静态合约通过。

## 10. 子目录知识无法摄取且旧 VectorStore 不会失效

- 问题：建立 20 类目录后，原 `Files.list()` 只看到根目录；Markdown 更新时仍会直接加载旧向量，但状态却按新文件计算。
- 原因：摄取只支持一层目录，持久化文件没有语料指纹。
- 修复：改为递归 `Files.walk()`，以下划线目录作为归档排除；语料路径、内容、切块版本与 Embedding 类型形成 SHA-256 manifest，指纹不匹配时原子重建向量与 manifest。
- 回归：PASS。30 个活动文件、20 类、62 Chunk 可重建并在下次上下文启动时以 `LOADED` 载入。

## 11. 同一政府报告拆成六类文档造成 URL 重复

- 问题：人口、经济、交通、教育、城市和政策六个文档曾共用同一政府工作报告 URL；canonical 去重会使后五类从向量库消失。
- 原因：资料整理按主题拆稿，却没有同步遵守“一个 canonical 来源只保留一个活动文档”的摄取规则。
- 修复：经济文档保留原报告，其余五类分别换成经核验的国家统计局、交通运输部、教育部与海南省政府独立来源；未绑定 Markdown 的资料改为 supporting。
- 回归：PASS。30 个活动 URL 重复数为 0，20 类都实际摄取；Source Index 为 30 active + 12 supporting。

## 12. Spring Boot 4 上下文没有 Jackson 2 ObjectMapper Bean

- 问题：第二轮实现后的首个全套测试出现 18 个 ApplicationContext error。
- 原因：新增服务构造函数注入 `com.fasterxml.jackson.databind.ObjectMapper`，而当前 Boot 4 自动配置边界没有提供该 Jackson 2 Bean。
- 修复：与既有持久化/Trace 组件保持一致，在知识和 Eval 服务内创建 `new ObjectMapper().findAndRegisterModules()`。
- 回归：PASS。Spring 上下文、JPA、Conversation、Memory 与 Agent 集成测试重新启动成功。

## 13. Windows 沙箱无法清理 JUnit 外部临时目录

- 问题：知识摄取测试主体通过，但关闭 JUnit extension context 时删除 `%TEMP%/junit-*` 报 `AccessDeniedException`。
- 原因：测试临时目录位于工作区外，受当前 Windows 沙箱权限限制。
- 修复：fixture 改到明确的 `target/ingestion-supervisor-test`，属于仓库内可写、可忽略的构建目录。
- 回归：PASS。专项资产/摄取/UI 合约测试 6/6 通过。

## 14. 地理问题的 Top 6 混入饮食文化

- 问题：25 条 Eval 首轮为 24/25；`GEO-01` 虽正确命中 geography，但第 4 条混入文昌鸡饮食文档（score 0.379267）。
- 原因：本地特征哈希 Embedding 对“文昌”等跨领域共现词敏感，低阈值 Top-K 会带入语义相邻但领域错误的结果。
- 修复：只在问题含明确领域词时构造 category metadata filter；Trace 检索与 `QuestionAnswerAdvisor` 共用同一 SearchRequest，开放式城市问题仍检索全库。
- 回归：PASS。`GEO-01` 只返回 geography / population_admin 相关资料；25 条知识与工具 Eval 全部通过。

## 15. 旧版运行进程锁住 JAR，导致 V1.4 clean 失败

- 问题：首次执行 V1.4 `clean package` 无法删除 `target/wenchang-brain-1.3.0-SNAPSHOT.jar`。
- 原因：V1.3 服务仍在 8080 运行，Windows 对正在使用的 JAR 保持文件锁。
- 修复：先确认 PID 仅属于仓库内旧服务，再停止旧进程；V1.4 打包完成后以 MCP profile 启动新 JAR。
- 回归：PASS。主应用构建与 46 项测试完成，8080 由 V1.4 接管。

## 16. 结构化政策与公共服务首轮字段不一致

- 问题：首轮资产把政策链接写为 `url`、分类写为单值 `category`、状态写为自然语言，公共服务缺少 `sourceId`；工具虽能返回条目，但来源、分类和状态字段丢失。
- 原因：知识采集字段与 Java Tool 契约未在写入前共用同一结构校验。
- 修复：政策统一为 `sourceUrl`、`categories[]` 与 `CURRENT / EXPIRED / SUPERSEDED / UNKNOWN`；公共服务、地点和政策全部绑定已进入 Sources Index 的 `sourceId`。
- 回归：PASS。政策 10、公共服务 24、地点 25 的必填字段均完整，重复 sourceId 与 URL 为 0。

## 17. MCP 结果投影视图遗漏 sourceId

- 问题：源 JSON 已有 `sourceId`，但 `searchPublicServices` 与 `searchStudyTourPlaces` 的投影视图只返回组织和 URL，主应用无法把 MCP 条目与 Sources Index 精确关联。
- 原因：MCP Server 为控制输出字段使用 `project(...)`，首轮字段白名单遗漏 `sourceId / source_id`。
- 修复：两个投影视图显式保留 sourceId，并在单元测试、真实 Streamable HTTP 调用与主应用 smoke 中增加断言。
- 回归：PASS。生产调用返回图书馆 `SRC-P1-062`、航天科普中心 `SRC-P0-060`，Trace 标记 `source=mcp`。

## 18. 公共服务 Agent Eval 被“文化”共现词带到文化语料

- 问题：Agent Eval 首轮为 6/7；`/公共服务` 已真实调用 MCP，但知识来源只命中文化类 Markdown，分类契约失败。
- 原因：Agent 执行时只传 Profile 类别，忽略 Skill 的 `preferredCategories`；RAG 又优先采用问题中的显式“文化”类别。
- 修复：有 Skill 类别时将其作为明确检索约束，并让公共服务 Skill 对齐 `public_services / population_administration / administrative_unit`；Advisor 与 Trace 复用同一 SearchRequest。
- 回归：PASS。Agent Eval 7/7，公共服务案例保留真实 MCP 工具调用。

## 19. 受限环境无法建立真实 MCP 测试回环连接

- 问题：MCP 单元测试通过，但 Streamable HTTP 集成测试在受限进程中报 `Unable to establish loopback connection`。
- 原因：测试需启动随机本机端口，当前沙箱禁止 Java NIO 建立回环连接；不是协议或业务实现错误。
- 修复：在获准的本机执行环境运行同一测试，不修改断言或跳过集成用例。
- 回归：PASS。MCP 模块 4/4，真实完成 `initialize / tools/list / tools/call`。

## 20. Assistant Markdown 被当作纯文本显示

- 问题：DeepSeek 返回的标题、列表、粗体、链接、代码块和表格在 SSE、完成态及历史恢复中显示为 Markdown 原文。
- 原因：三条渲染路径都直接写入 `textContent`，项目没有 Markdown parser、sanitizer、raw buffer 或完成态重绘。
- 修复：本地保存 marked 与 DOMPurify，建立唯一 `renderMarkdown()`；SSE 使用 raw buffer + debounce，complete 与历史恢复做完整安全渲染，外链补充 `noopener noreferrer`。
- 回归：静态 DOM 契约、marked 解析、Node 语法及浏览器 DOM 验收覆盖 h1/h2/strong/ul/ol/a/code/pre/table。

## 21. 搜狗 SearchProvider 返回 HTTP 302 反爬页

- 问题：真实问题“我想知道最新的文昌政策”中，`webSearch` 返回 `IllegalStateException - HTTP 302`；权威检索也无法取得动态结果。
- 实际 Trace：DeepSeek、CapabilityRouter、Native Registry 与 ToolCallback 均正常；302 Location 指向搜狗 antispider 页面，MCP 仍独立正常。
- 原因：现有 HTML 搜索 Provider 被目标站反爬，不是 MCP、DeepSeek Tool Calling 或一般网络断连。
- 修复：建立可替换 SearchProvider、真实 `healthCheck()`、`ANTI_BOT` 错误分类和 Last Success/Error；页面不再把 enabled 误报为 READY，保留 Search API Key 配置入口。
- 当前状态：故障已准确隔离并可观测；免费 HTML Provider 仍不可作为生产稳定搜索，需配置稳定搜索 API 后才能恢复动态搜索 SLA。

## 22. Tool 失败文本被 Trace 记录为成功

- 问题：WebSearchTool 会把异常转换成“联网搜索失败”字符串，旧 TraceableToolCallback 因没有抛异常而记录成成功，且缺 stage/status/errorType。
- 原因：Trace 只保存 input/output/latency，没有结构化失败判定，也使用含糊的小写 source。
- 修复：统一 ToolCallTrace 字段，识别 UNAVAILABLE/失败文本并标记 FAILED；toolSource 强制为 NATIVE/MCP，保留 stage、errorType 与脱敏输入输出。
- 回归：Trace 定向测试覆盖异常与失败文本，两类均输出明确状态。

## 23. ArtifactService 注入不存在的 Jackson 2 Bean

- 问题：Artifact API 首次加入后，Spring 上下文因找不到 `com.fasterxml.jackson.databind.ObjectMapper` Bean 全部失败。
- 原因：Boot 4 当前自动配置边界未暴露该 Jackson 2 Bean，与知识服务曾出现的问题相同。
- 修复：ArtifactService 内部创建并注册模块的 ObjectMapper；manifest 时间使用稳定 ISO-8601 字符串。
- 回归：主应用上下文、Artifact API 与全量测试恢复。

## 24. Artifact 测试在系统 TempDir 上误报 404

- 问题：manifest 可列出，但 download/delete 在测试中返回 404，JUnit 清理阶段又报 AccessDenied。
- 原因：生产代码的 `toRealPath()` 安全校验访问了工作区外受限 `%TEMP%`，不是 relativePath 协议错误。
- 修复：fixture 移至 `target/test-artifacts`，保留生产 real path 与 symlink 防护，Files.walk 使用 try-with-resources 关闭句柄。
- 回归：ArtifactControllerIntegrationTest 2/2 PASS。

## 25. 真实 DeepSeek Key 仅存在运行时覆盖

- 问题：模型实际连接成功，但仓库没有 `config/local-secrets.properties`，进程/用户/机器环境变量也没有 Key；服务重启会回到 UNCONFIGURED。
- 原因：本轮 Key 通过设置页写入进程内 Runtime Override，按安全设计不会从状态 API 回传，也不会自动落盘。
- 处理：重启前完成真实 Chat、Native Tool 与 MCP Tool 留证；后续生产运行需用户再次在设置页输入，或自行写入已被 Git 忽略的 server-default 配置。
- 当前状态：密钥未泄露；重启后的真实 diagnosticEcho 仍依赖恢复这一外部状态。

## 26. 生产 Agent Eval 的长会话 ID 使 AgentRun 持久化失败

- 问题：四个生产 Artifact 任务和原有 Agent 案例都已执行工具，但 11 项 Eval 首轮全部显示 `PERSISTENCE_FAILED`。
- 根因：评测会话 ID 是 `agent-eval-<UUID>`，长度 47；`AgentRunEntity.conversationId` 仅允许 36 字符，H2 报 `SQLState 22001`。
- 修复：AgentRun 与 AgentApproval 的 conversationId 统一放宽到 128，并加入长评测会话 ID 持久化回归。
- 回归：PASS。主应用 58/58，生产 Agent Eval 11/11，所有 runStatus 均为 `COMPLETED`。

## 27. Word Artifact 缺少纸张与页边距元数据

- 问题：Apache POI 能重新打开生成的 DOCX，但标准渲染器无法从 `document.xml` 找到 `sectPr`，不能确定页面尺寸。
- 根因：首轮 `WordArtifactWriter` 只创建段落与样式，未显式写入 section/page properties。
- 修复：为所有 Word Artifact 写入 A4 纸张、1 英寸页边距、页眉页脚距离与 gutter，并在 POI 重新打开测试中断言 `sectPr / pgSz / pgMar`。
- 回归：PASS。MCP 模块 7/7，修复后的政策简报、研学方案和专题 Word 已由生产 Agent 重新生成并通过 POI 打开。

## 28. “初二学生”在主应用被降级为“全年龄”

- 问题：可见 Word 验收发现某次 `AGENT-STUDY-01` 研学方案虽生成文件，但地点与来源为 0。
- 根因：MCP Server 已支持初一/初二/初三，但主应用 `extractAgeGroup()` 只识别“初中/七至九年级”，“初二”被转成“全年龄”，结构化地点筛选因而返回空集。
- 修复：主应用保留初一/初二/初三精确年级；生产 Agent Eval 对预期 Word 的案例新增 `artifact.sourceCount > 0` 硬性检查。
- 回归：PASS。最终 `AGENT-STUDY-01` 为 `COMPLETED`，研学 Word 包含 8 个可追溯来源；增强后生产 Agent Eval 仍为 11/11。

## 29. 已选 Agent 后打开 Skill Selector 会立即关闭

- 问题：先选择 Policy Assistant，再点击 Agent Context 中的“/ 使用技能”，Skill Selector 会短暂出现后立即回到已选 Agent 状态。
- 根因：按钮处理器先重绘了 Context DOM，原按钮随即脱离文档；同一次点击继续冒泡到 document 时，旧的 `event.target.closest()` 已无法判断它来自 Command Bar，于是被误判为外部点击并关闭选择器。
- 修复：外部点击判断改用本次事件稳定的 `event.composedPath()`，检查路径是否包含 Command Bar；即使 Context 在处理过程中重绘，也不会丢失事件来源。
- 回归：PASS。浏览器实测 Policy Assistant + `/政策简报` 进入 `AGENT_AND_SKILL_SELECTED`，两个 Context 同时存在，未创建空会话。

## 30. Artifact successfully created but download entry missing

- 问题：模型正文明确声称 Word 已生成，但回答下方没有文件卡、下载按钮或可点击链接。
- 实际链路：MCP 已生成文件；模型却给 Artifact Tool 传入语义化 `conversationId`，与主应用真实会话 UUID 不同。文件落在错误目录，主应用按真实会话查询时得到空数组。Spring AI MCP 响应又是嵌套 content 包装，旧采集器未能取得 artifactId，因此 Chat Response、SSE、Message 与 UI 同时丢失。
- 修复：仅对 4 个 Artifact MCP Tool 使用服务端 `ToolContext` 强制覆盖 conversationId/Agent/Skill；递归解析 MCP 包装结果并收集 artifactId；按 ID 构造统一 ArtifactDescriptor；贯通 AgentRun、AgentStep、Chat Response、SSE、Message 与前端卡片。
- 防伪规则：只有 Registry 中存在可下载 Artifact 才允许保留“已生成”措辞；失败时正文改为明确的文件成果未完成。
- 回归：本地 Word/XLSX/CSV 与公网 Word 均返回结构化 Artifact。公网下载 200，Content-Length 与文件一致，UTF-8 中文文件名正确；刷新和 systemd 重启后仍可下载。

## 31. MCP 查询工具被注入 Artifact 上下文字段

- 问题：上线后的第一次 `/公共服务` + Word 验收中，`searchPublicServices` 报严格 schema 不允许 `conversationId / createdByAgent / skillId`，查询失败且无文件。
- 原因：Artifact 会话归属修复最初按 `toolSource=MCP` 统一注入字段，没有区分查询型 Tool 和成果型 Tool。
- 修复：上下文覆盖只作用于 `createWenchangWordReport / exportWenchangData / createStudyTourPackage / createPolicyBrief`；查询 Tool 输入保持原始 schema。明确文件意图由编排器确定性追加成果 Tool。
- 回归：新增查询输入不变测试与 Skill Artifact 路由测试；生产真实调用工具序列为 `searchPublicServices, createWenchangWordReport`，Agent Run `COMPLETED`，文件数 1。
