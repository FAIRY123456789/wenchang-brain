# 文昌智脑部署文件

目标拓扑：Nginx `/wenchang-brain/` → `127.0.0.1:18080` → MCP `127.0.0.1:18091`。

1. 先按 `docs/deployment/ALIYUN_DEPLOYMENT.md` 完成服务器只读预检与容量门禁。
2. 将冻结 Release 上传到服务器临时目录并核对 SHA-256。
3. 使用服务器实际 Java 17 绝对路径运行：`sudo bash deploy/deploy.sh <release-dir> <java17-bin>`。
4. 在 `/opt/wenchang-brain/config/local-secrets.properties` 填写服务器密钥并保持 `0600`。
5. 备份目标 Nginx 配置，加入 `nginx-wenchang.conf.example` 中的 location；`nginx -t` 成功后只 reload。
6. 回滚使用部署输出的 rollback metadata：`sudo bash deploy/rollback.sh <metadata-file>`。

脚本不包含、上传或覆盖真实 API Key，也不修改已有 Python、Node、数据库或其他服务。
