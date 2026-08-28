# 文昌智脑 V1 · 阿里云 ECS 部署记录

## 发布状态

2026-08-21 已完成五语系统界面升级并同步正式环境。当前 Release 为 `1.5.0-i18n-20260821`，Git Commit 为 `3e35f5f47b3357fd01caf1f06416d5f0f0dd61a1`；公网地址为 `http://120.26.238.159/wenchang-brain/`。服务器仍仅开放现有 80 端口，主应用与 MCP 保持 loopback。

### 2026-08-21 · 五语系统界面更新

- 设置抽屉新增系统语言选择：中文（默认）、English、Bahasa Indonesia、العربية、Português。
- 语言选择保存在浏览器本地存储，刷新后继续生效；阿拉伯语自动切换为 RTL，其余语言保持 LTR。
- Release SHA-256：`9487f61f66daece4e472c9fe37a4a5e4a47a76bfc81edadcf9d50292b7e55150`；归档逐文件校验通过，未包含 Git、Maven 缓存、真实 Secrets、H2、历史 Artifact 或 Research 数据。
- 本地门禁：主应用 85/85 PASS，MCP 7/7 PASS；`i18n.js` 与 `app.js` JavaScript 语法检查通过。
- 预发布：独立目录、`127.0.0.1:18082`、隔离 H2/Artifact/Research/Trace、MCP 与 Web Search 关闭；健康、Agent、Skill、五语资源及 MIME 验收通过后停止临时实例。
- 正式发布：`wenchang-brain`、`wenchang-mcp`、`nginx` 均 active，主/MCP `NRestarts=0`；RAG 50 files / 136 chunks，VectorStore `LOADED`，DeepSeek 配置仍为 `REMOTE_DEFAULT / deepseek-chat`。
- 公网验收：首页、`i18n.js`、`app.js`、`styles.css`、Health、Agent、Skill、Conversation 均 HTTP 200；既有 `/` 与 `/future-bay-eco-lab/` 保持 HTTP 200；Nginx 配置检查通过。
- Secrets：生产 `local-secrets.properties` 未进入 Release，部署后仍为 `0600 wenchang:wenchang`，验收过程未输出 Key 明文。
- 回滚点：`/opt/wenchang-brain/backups/predeploy-i18n-20260821T080553Z.properties`；部署脚本回滚元数据为 `/opt/wenchang-brain/backups/rollback-20260821T080553Z.properties`。

## 固定拓扑

- 目标 ECS：`120.26.238.159`
- 公网路径：`http://120.26.238.159/wenchang-brain/`
- Nginx：复用现有 80/443，只新增 `/wenchang-brain/` location
- 主应用：`127.0.0.1:18080`
- MCP：`127.0.0.1:18091`
- 应用目录：`/opt/wenchang-brain/`
- 运行用户：`wenchang`
- Secrets：`/opt/wenchang-brain/config/local-secrets.properties`，权限 `0600`
- H2：`/opt/wenchang-brain/data/chat/wenchang-chat`
- VectorStore：`/opt/wenchang-brain/data/wenchang-vector-store.json`
- Artifact：`/opt/wenchang-brain/data/artifacts/`
- Research Dataset：`/opt/wenchang-brain/data/research/`
- Knowledge：`/opt/wenchang-brain/knowledge/`

## 本地冻结

- Version：`1.5.0-SNAPSHOT` / 产品标识 `V1.5`
- 主 JAR：`target/wenchang-brain-1.5.0-SNAPSHOT.jar`
- MCP JAR：`extensions/wenchang-public-resource-mcp/target/wenchang-public-resource-mcp-1.4.0-SNAPSHOT.jar`
- 主应用测试：72 tests，0 failure，0 error
- MCP 测试：7 tests，0 failure，0 error
- Base Path：静态资源、fetch、SSE、Artifact、审批和 Logo 均经过统一 `APP_BASE_PATH` 适配
- 主 JAR SHA-256：`3344be68f74772128cf6f4127866d6d5655cf21aa39a863218c9462f9194783c`
- MCP JAR SHA-256：`29663181134c262d9416ee783aa235f78e9989836fa2361a00cb57163a63c926`
- Git Commit：`UNCOMMITTED`（仓库当前没有可引用提交，不伪造 commit）

## 生产环境实测

- OS：Alibaba Cloud Linux 3.2104 U13.2，x86_64
- Java：OpenJDK 17.0.20 Headless；systemd 使用明确的 Java 绝对路径
- 容量：1.8 GiB RAM、无 Swap；上线后 MemAvailable 约 918 MiB；根盘可用约 17 GiB
- MCP：active/enabled，7 tools，当前启动周期 0 error / 0 restart
- Main：active/enabled，当前启动周期 0 error / 0 restart
- RAG：50 files / 136 chunks；VectorStore `LOADED`，corpusSignature=`23194410bf6a1a3c9869edadb773d92d47188257c91f296ca08af3689f0ddf75`
- DeepSeek：`REMOTE_DEFAULT / deepseek-chat`，真实 connection 和 diagnosticEcho Tool Calling PASS
- Search：Tavily `AVAILABLE`；官方检索真实得到 6 条官方白名单结果；Brave 未配置（可选）
- MCP 协议：initialize=200、protocol=2025-11-25、tools/list=7、tools/call=200/isError=false
- Agent：政策简报完成 7 steps 并调用 5 个工具；DeepSeek 也真实调用 MCP `searchPublicServices`
- Artifact：Word 与 XLSX 均真实生成，公网下载 HTTP 200，并用文档库重新打开；中文、来源、URL 均通过
- Conversation：服务重启前后消息、Agent、Skill、来源和 Agent Run 保持不变
- SSE：公网 101 个 `answer_chunk`，最终 `complete`，无 error
- 浏览器：首页/资源/Command Bar/Composer/历史/Artifact 卡片通过；无水平溢出；页面应用 Console 0 error
- Existing Sites：`/` 与 `/future-bay-eco-lab/` 上线前后均 HTTP 200
- Nginx backup：`/etc/nginx/conf.d/htmlsite.conf.bak-20260812T024602Z-pre-wenchang`
- Rollback metadata：`/opt/wenchang-brain/backups/rollback-20260812T024359Z.properties`

## 首次连接与只读预检

SSH 参数不得写入仓库；本地使用被 `.gitignore` 排除的 `config/deploy-local.properties`。连接成功后，先只读执行并保存：

```bash
cat /etc/os-release
uname -a
uname -m
hostnamectl
free -h
df -h
uptime
java -version
which java
readlink -f "$(which java)"
python --version || true
python3 --version || true
node --version || true
npm --version || true
nginx -v
systemctl --type=service --state=running
ss -lntp
ps aux --sort=-%mem | head -30
du -sh /opt/* 2>/dev/null
```

必须确认 18080/18091 未占用、Java 17+ 可用、内存/磁盘足够、已有 Nginx owner 配置和现有站点基线状态。不得修改系统默认 Python、Node 或全局 Java alternatives。

## Release 安装

上传并解压 UTF-8 `tar.gz` Release 后，先执行 `sha256sum -c checksums.sha256`。不使用 Windows `Compress-Archive`，避免中文知识文件名在 Linux 解压时损坏。选择服务器现有 Java 17+ 的真实绝对路径；如不兼容，则在 `/opt/wenchang-brain/runtime/` 安装隔离 Java 17，不能替换全局默认 Java。

```bash
sudo bash deploy/deploy.sh <extracted-release-dir> <absolute-java17-bin>
```

脚本创建版本目录、持久目录、低权限用户、systemd unit、当前版本 symlink 和回滚 metadata。它不会覆盖已经存在的服务器 Secrets。

## Secrets

服务器首次安装会由空模板创建：

```text
/opt/wenchang-brain/config/local-secrets.properties
```

仅由 root 或 `wenchang` 用户读取，至少填写当前项目定义的 DeepSeek 与 Tavily 字段；Brave 可选。不要在聊天、日志、Release、Git 或命令历史中传递明文 Key。

```bash
sudo chmod 600 /opt/wenchang-brain/config/local-secrets.properties
sudo chown wenchang:wenchang /opt/wenchang-brain/config/local-secrets.properties
sudo systemctl restart wenchang-brain
```

## 服务操作

```bash
sudo systemctl start wenchang-mcp wenchang-brain
sudo systemctl stop wenchang-brain wenchang-mcp
sudo systemctl restart wenchang-mcp
sudo systemctl restart wenchang-brain
sudo systemctl status wenchang-mcp wenchang-brain --no-pager
sudo journalctl -u wenchang-mcp -n 200 --no-pager
sudo journalctl -u wenchang-brain -n 200 --no-pager
```

systemd 使用明确 Java 绝对路径、loopback 地址、有限 JVM Heap、`Restart=on-failure` 和文件系统写权限白名单。针对 1.8 GiB、无 Swap 的当前 ECS，主应用为 `Xmx320m / MemoryMax=560M`，MCP 为 `Xmx128m / MemoryMax=320M`。

## 内部 Health Check

```bash
curl -fsS http://127.0.0.1:18091/actuator/health
curl -fsS http://127.0.0.1:18080/api/health
curl -fsS http://127.0.0.1:18080/api/admin/diagnostics/agent
curl -fsS http://127.0.0.1:18080/api/agent/tools
```

只有 MCP、RAG、VectorStore、Conversation、DeepSeek、Tavily 与 Artifact 的真实状态满足验收，才允许接入公网。

## Nginx

先找出当前 server block 的实际 owner 文件并备份。将 `deploy/nginx-wenchang.conf.example` 中两个 location 加到该 server block，不能覆盖已有 root 或其他 location。

```bash
sudo cp -a <nginx-owner-file> <nginx-owner-file>.bak-<UTC_TIMESTAMP>
sudo nginx -t
sudo systemctl reload nginx
```

若 `nginx -t` 失败，立即恢复备份；只能 reload，不能因本项目 restart Nginx。上线前后分别验证首页、`/future-bay-eco-lab/` 以及侦察所得其他现有路径。

## 公网验收

最终地址为 `http://120.26.238.159/wenchang-brain/`。静态资源、Conversation、服务重启后 H2 恢复、DeepSeek SSE、Markdown、Agent、Skill、RAG、一次 Tavily 诊断查询、MCP initialize/tools/list/tools/call、Word/XLSX Artifact 生成与 HTTP 200 下载均已通过；浏览器页面 Console 0 error。

## 更新与回滚

每次升级都从本地构建新 Release，上传并切换 symlink，禁止在生产服务器修改源码。部署命令会输出 rollback metadata：

```bash
sudo bash deploy/rollback.sh /opt/wenchang-brain/backups/rollback-<timestamp>.properties
```

若已经接入 Nginx 且必须撤回路由，恢复对应 Nginx 备份，先 `nginx -t`，成功后仅 reload。

## 未记录的敏感信息

本文不记录密码、API Key、SSH 私钥内容或私钥文件名。服务器配置必须使用 `vim`，禁止使用 `nano`；所有检查仅输出配置布尔值或脱敏状态。

## 2026-08-21 · 语言切换可靠性热修复

- Release：`1.5.1-language-ui-fix-20260821`
- Git Commit：`0dc30e831830cc548d42aee29c0bd14fa78855cc`
- Archive SHA-256：`822a435f56b5e50a001bd4ea9762e995a367624142993a8460ad3bb1a1c23540`
- 本地门禁：Maven 85/85 PASS；Node 语法 PASS；受限本地存储运行时切换 PASS；本轮未调用 DeepSeek/Tavily。
- Canary：`127.0.0.1:18082`，独立 H2/Vector/Artifact/Research/Trace，无生产 Secret、MCP 和 Search；Health、新版 HTML、`i18n.js?v=1.5.1-language-ui`、`styles.css?v=1.5.1-language-ui` 均通过后停止。
- 正式切换：`/opt/wenchang-brain/app/current.jar` 与 `/opt/wenchang-brain/mcp/current.jar` 已指向新 Release；Main/MCP/Nginx active，NRestarts=0。
- 公网：`http://120.26.238.159/wenchang-brain/` 及版本化 JS/CSS HTTP 200；`/` 与 `/future-bay-eco-lab/` 保持 HTTP 200。
- 运行状态：DeepSeek `REMOTE_DEFAULT/deepseek-chat`；RAG `READY`，50 files/136 chunks；MCP `UP`；没有读取或输出 Secret 明文。
- 回滚：`sudo bash deploy/rollback.sh /opt/wenchang-brain/backups/rollback-20260821T121238Z.properties`
- 验收限制：桌面应用的可视化浏览器连接被本机沙箱组件故障阻断；未将自动点击标记为 PASS。公网内容、无存储运行时和 canary 证据均已通过，用户端可直接刷新并验证视觉交互。
## 2026-08-28 · 诊断状态与消息操作热修复

- Release：1.5.2-diagnostics-actions-20260828
- Git Commit：45447d5610960a6fbe6f60ee86269d7b2f78bb0e
- Archive SHA-256：10be8e37474027cc8c3193aac541452724006b15e874da505f1c9997c0e1c1c3
- 变更：修复 SearchProvider health 与 UI 判定字段错配；每条用户问题新增复制和编辑入口，编辑仅载入 Composer 并保留历史。
- 本地门禁：Maven 85/85 PASS；Node 语法与 Git diff 检查 PASS；本轮未调用 DeepSeek/Tavily。
- Canary：127.0.0.1:18082，独立 H2/Vector/Artifact/Research/Trace，不加载生产 Secret、MCP 或 Search；诊断双字段、新版 HTML/JS/CSS、复制/编辑资源均 PASS 后停止。
- 正式切换：Main/MCP/Nginx active，NRestarts=0；当前 JAR 指向 /opt/wenchang-brain/releases/1.5.2-diagnostics-actions-20260828/。
- 公网：http://120.26.238.159/wenchang-brain/ 和版本化 JS/CSS HTTP 200；/ 与 /future-bay-eco-lab/ 保持 HTTP 200。
- 运行状态：DeepSeek=REMOTE_DEFAULT/deepseek-chat；RAG=LOADED，50 files/136 chunks；搜索在进程重启后为 UNKNOWN / NOT_CHECKED，等待用户下次自检按真实 Provider 结果更新。
- 回滚：sudo bash deploy/rollback.sh /opt/wenchang-brain/backups/rollback-20260828T005816Z.properties
- Secret：生产 Key 未读取、未输出、未进入 Release；服务器配置未使用 nano。
- 验收限制：桌面应用浏览器自动控制连接连续两次被运行组件终止，未将可视化自动点击标记为 PASS。
## 2026-08-28 · Artifact UI 与持久化问题版本发布

- Release：`1.5.3-conversation-branches-20260828`
- 源码 Commit：`2e36b4823a5589ebca371d4f45d2046da194fb7c`
- 归档 SHA-256：`b08ed244662ae68b695c4b50060c0810e42b440948ca13d4a547bf4639b69600`
- 本地门禁：Maven clean package 87/87 PASS；Node `app.js / i18n.js` 语法 PASS；Git diff check PASS；未调用 DeepSeek/Tavily。
- Canary：`127.0.0.1:18082`，无 Secret、禁用 Search/MCP、独立内存 H2 与运行目录；新版缓存标识、原位编辑、分支切换资源和 Agent/Skill API 通过，生产服务全程 active，验收后停止。
- 数据库迁移：Hibernate update 为现有 H2 增加 Message parent/revision 字段及 Conversation active leaf；上线后原有 15 个 Conversation 可读取，未清空或覆盖历史数据。
- 正式状态：Main/MCP/Nginx active，NRestarts=0；18080/18091 继续只监听 loopback；Nginx `-t` PASS；Secret 保持 `0600 wenchang:wenchang`。
- 公网验收：产品、版本化 JS/CSS、Health、Conversation、原有 `/` 与 `/future-bay-eco-lab/` 共七项 HTTP 200；资源中存在 inline editor 与 activate API，不再包含重复“打开”按钮或模型标签渲染。
- 回滚：`sudo bash deploy/rollback.sh /opt/wenchang-brain/backups/rollback-20260828T060901Z.properties`
- 浏览器限制：桌面浏览器控制运行内核被 Windows 沙箱 helper 错误终止，未执行可视化自动点击，也未虚报为 PASS；由 87 项回归、canary 和公网静态/HTTP 契约覆盖自动化验收。
- Secret：未读取、未输出、未进入归档；未查看生产日志或 Trace；服务器配置未使用 nano。
## 2026-08-28 · 编辑分支 Artifact 隔离与任务边界发布

- Release：`1.5.4-edit-boundaries-20260828`
- 源码 Commit：`d105bf9adae97387d63e7c9afe06caae77aac6b2`
- 归档 SHA-256：`edad31c1d0ba0ba1fc6aeef57941259c3989ba0d5addde3d7e8a690beb65951e`
- 本地门禁：主应用 Maven clean package 91/91 PASS；MCP 非网络业务 6/6 PASS；Node `app.js / i18n.js` 语法 PASS；Secret pattern count 为 0；未调用 DeepSeek/Tavily。
- 本机限制：MCP HTTP 集成测试唯一失败来自 Windows 沙箱 Java NIO loopback，非工具逻辑断言失败；正式 ECS 随后完成真实 MCP 网络验收。
- Canary：`127.0.0.1:18082`，内存 H2、独立 Artifact/Research/Trace、禁用 Search/MCP、不加载 Secret；Health、编辑 UI、Enter/IME、无会话级 Artifact 回填与当前 Message metadata 渲染 PASS。
- MCP 正式验收：`initialize=200`、`tools/list=200`、`toolCount=7`、`tools/call=200`、`isError=false`，只读 `searchTownshipProfile(龙楼镇)` 返回有效内容。
- 正式状态：Main/MCP/Nginx active，NRestarts=0；当前 JAR 指向 `/opt/wenchang-brain/releases/1.5.4-edit-boundaries-20260828/`。
- 公网：产品、版本化 JS/CSS/i18n、Health 以及既有 `/`、`/future-bay-eco-lab/` 全部 HTTP 200；发布后未触发外部模型或搜索调用。
- 回滚：`sudo bash deploy/rollback.sh /opt/wenchang-brain/backups/rollback-20260828T093542Z.properties`
- 浏览器限制：桌面浏览器控制内核被 Windows sandbox helper 连续终止，未把可视化点击虚报为 PASS；UI 合同测试、实际 JAR、canary 与公网 HTTP 均已通过。
- Secret：未读取、未输出、未进入 Release；未查看生产 Trace；服务器配置未使用 nano。
