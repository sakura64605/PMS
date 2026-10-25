# governance

内容审核和举报。

`api` 放入口及对外契约，`application` 放业务用例，`domain` 放已有领域规则，`infrastructure` 放持久化、缓存、消息和配置。跨模块协作优先使用 API 或事件；现有内部调用按依赖基线逐步收敛。

[完整模块边界和拆分说明](../../../../../../../../../../docs/architecture/module-boundaries.md)
