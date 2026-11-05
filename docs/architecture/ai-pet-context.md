# AI 宠物上下文（2026-11-05）

用户在 AI 页面维护名字、物种、品种、出生日期、性别；未知字段留空。这是咨询资料，不是医疗档案，不能从领养/救助帖子推断宠物归属。

请求携带 petId；后端根据认证用户验证归属并重建 petContext，不信任客户端 userId/petContext。新会话绑定后不可改绑；切换宠物开启新咨询，历史会话恢复原绑定。删除档案保留聊天历史，但拒绝继续引用该档案。每轮读取最新资料，按上海日期计算月龄，提示模型核实档案冲突和历史症状发生时间，不自动修改档案。

绑定宠物的会话不读取或写入旧的用户级长期记忆，以免未标注宠物的记忆串档；仍使用本会话历史与摘要。症状来源和时间仍依赖提示词约束，未新增结构化健康事件。

## 数据与接口

迁移：database/manual/ai-pet-profile.sql，新增 ai_pet_profile 和 ai_chat_session.pet_id。执行 ALTER 前检查 information_schema；初始化 schema 已同步。AI 账号仅增加新表 SELECT/INSERT/UPDATE/DELETE 权限。

接口：/pet-system/ai/pets GET/POST；/pet-system/ai/pets/{id} PUT/DELETE。全部登录验证，修改/删除条件含用户归属。

## 验证与限制

- 42 项后端测试通过（新增 9 项），全部 mock，不调用模型。
- 前端构建成功，94 项组件/逻辑测试通过，覆盖恢复宠物会话、切换宠物清除旧历史。
- 线上网关验证 CRUD、未登录拒绝、跨用户修改/删除拒绝、清空生日、未来日期拒绝及会话列表。测试档案已删除。
- 注册了四个普通验收账号：首次脚本在未登录空响应解析处中断，修正后重新验收。未发布内容。
- 浏览器工具启动失败，未完成真实浏览器视觉验收；真实 AI 对话请求为 0，用户授权前不得发起。
- 可选 MySQL 集成测试未运行，依赖注入已适配，新测试库需要更新 schema。

## 发布与回退

AI 与 React 已发布，Core/Gateway 未更换。备份目录 /opt/pms/releases/pet-context-20261105/，包含 ai.backup.jar、frontend.backup.tar.gz、session-schema.backup.txt 和保留配置的 ai.staged.jar。

线上外部配置未改；旧 JAR 两个 application 配置与新包逐字比对一致。pms、pms-ai、pms-cloud-gateway 均 active；8088 业务接口、8281 health 和域名 HTTPS 入口通过。

回滚恢复旧 AI JAR 和前端备份，重启 pms-ai；新增表/可空字段保留，保护已写数据，旧版本忽略它们。实现保留并兼容工作区已有未提交的上下文压缩改动。
