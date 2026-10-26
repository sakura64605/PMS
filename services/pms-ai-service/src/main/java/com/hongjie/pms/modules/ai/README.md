# AI service business module

会话、记忆、Agent、模型、转人工及知识库现在由 `services/pms-ai-service` 独立运行。

工具通过核心内部 HTTP 接口读取其他业务的数据，不引用其实体或 Mapper。协议在 `contracts/ai-contracts`，共享运行设施在 `components/service-runtime`。

详见仓库 `docs/architecture/ai-service-extraction.md`，包含运行配置、验证范围和协调发布步骤。
