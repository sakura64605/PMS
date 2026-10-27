# 首页与 AI 服务协调发布（2026-10-27）

源版本 `9a9c157`（含 `0733d7b` AI 抽取）。北京时间09:28完成发布，并完成后续真实接口验证。

- 首页：http://111.229.254.252/dashboard 。
- 发布目录：`/opt/pms/releases/ai-dashboard-9a9c157-20261027-r4`。
- 核心：`/opt/pms/pms.jar`，SHA256 `4b7bbbb44b7cf4947b781f3df1c2318db7a93fd3b7295b3bd4e1cc2a0d73cef5`。
- AI：`/opt/pms/ai/pms-ai-service.jar`，SHA256 `2f380a15894395ef8f6bf049f4cce1f2cf09f63fd8f0ae45ee0e48e3e7215677`，systemd `pms-ai`，loopback8281，已启用开机启动。
- 网关：原服务单元继续使用原JAR路径，已更新实现，SHA256 `04380ea4b16cab6be7aba5b08c22a64c1b561b2f77f7f2d4a495af5c678150c3`。

## 配置与数据库

原核心五份应用配置逐字节保留。模型参数、记忆开关、Redis及Qdrant设置在服务器本地迁至AI私有配置，未下载密钥。AI外部配置与核心服务间凭据文件权限600，AI目录700；静态资源目录755、文件644。

没有改表、搬表或清空缓存。AI创建专用账号 `pms_ai_9a9c157`，只有五张AI表的SELECT/INSERT/UPDATE/DELETE权限；实测读取user表被拒绝。核心增加 `/etc/systemd/system/pms.service.d/ai.conf`，加载 `/opt/pms/ai/core.env`；原Cloud与XXL-JOB配置未改。

## 验证

首页依赖的公告、未读数、报名统计、宠物预览、活动和日记等接口通过。公网页面入口与本次React构建一致，三个入口资源可读取。宠物列表含限长正文预览与地点。

通过临时用户真实请求验证：AI HTTP对话的转人工分支、历史、反馈、转人工、清除短期记忆、长期记忆增删改；通过Nginx→Gateway→AI建立真实WebSocket并收发消息。七个内部业务工具均成功返回，模型参数中的用户ID不改变查询归属。普通用户被拒绝管理知识库。

本次没有调用付费模型API；保留原模型配置并验证了实际运行进程和消息链路，真实生成效果可在AI顾问页面另行体验。最终七个服务active。临时账户及其AI消息、会话、转人工和记忆记录已删除。

服务器结果文件：`deployment.json`、`final-verification.json`。

## 发布过程中处理的问题

前三次尝试均自动回滚：第一次首次搜索触发4秒超时；第二次检查脚本未兼容网关401空正文；第三次私有临时目录权限被copytree复制到静态assets，导致资源检查失败。已加入启动期重试、空正文解析兼容、流式JAR切换及明确的静态目录权限，恢复旧资源访问后第四次发布全部通过。最终一轮启动期重试次数为0。

验证脚本的临时密码长度与消息清理SQL也已按实际业务规则和表结构修正；失败尝试创建的临时记录已核对清理。

## 回滚

发布目录保留 `core-before.jar`、`gateway-before.jar`、`index-before.html`、`current-before.json`。核心原版本为 `79e2da5`，前端原版本为 `c142345`。回滚必须一起恢复核心与网关、恢复旧前端入口，并停用新AI服务/移除本批新增核心AI drop-in；仅替换其中一个应用会造成路由与实现不匹配。AI表没有结构变化，原数据不应删除；专用数据库账号和私有配置可保留供重发。
