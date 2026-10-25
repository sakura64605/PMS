# 全局搜索查询重构

## 范围与兼容

本批接管 GET /pet-system/search/global 查询链路。
保留原请求参数、UnifiedSearchResponse / SearchResultItem、实体、数据库表和旧 UnifiedSearchService。
PmsUnifiedSearchService 继承旧具体类并使用 @Primary，现有控制器无需修改。
搜索建议仍保留原有空列表行为；ES 文档同步、全量同步、索引重建暂沿用原实现，
不将本批称为整个搜索模块迁移完成。

## 分层与扩展

- SearchInput：统一默认值和参数校验。
- SearchBackend：搜索召回端口，只返回类型、业务 ID、候选总数。
- ElasticsearchSearchBackend：ES 查询与排序；仅请求 type、businessId 两个 source 字段。
- SearchSource / SearchSources：宠物、活动、日常的数据库批量读取策略。
- SearchProjection：复用 StrategyRegistry 注册策略，分组回查、按 ES 顺序输出并去重。
- SearchEnrichment：批量装配当前作者资料和当前用户互动状态。
- SearchHighlights：基于当前数据库文本生成安全高亮。
- PmsUnifiedSearchService：兼容原调用入口，编排召回与装配。

ES 网络请求先完成，随后才开启数据库只读 REPEATABLE_READ 事务。
事务不覆盖远程 ES 调用，避免等待 ES 时占用数据库事务。
作者、点赞、收藏、报名均按批次读取，不逐条查库。
日常话题复用 DailyAssembler.topicMap，只展示当前启用的话题。

## 修复行为

ES 负责候选和排序，不再直接把旧正文、计数、作者资料返回前端。
候选再次用数据库校验：
- 宠物：访客仅可见审核通过且发布/完成的帖子；作者可预览自己的非删除帖子。
- 活动：删除内容任何人均不返回；访客仅可见审核通过且报名中/进行中/结束的内容。
- 日常：只允许 status=1；未通过审核的内容仅作者自己可见。

统一搜索保留原“通过审核或本人内容”的召回范围，不扩展管理员全站待审搜索。
未知类型、空/非法 ID、重复候选被过滤；不同类型相同 ID 不互相覆盖。
图片保持单张封面，不暴露联系手机号、密码等实体字段。

补齐原 TODO：
- 点赞按 pet_post / pet_activity / daily 与目标 ID 联合匹配。
- 宠物收藏按当前用户、pet_post 和目标 ID 匹配。
- 报名/签到/爽约显示为已报名 1，取消或无记录为 0。
- 未登录不查询个人互动记录，返回 false/0。
- 作者昵称、头像及计数按数据库当前值返回，不缓存搜索响应 DTO。

高亮使用当前正文进行大小写不敏感的关键词字面匹配。
所有文本、匹配片段先 HTML 转义，只允许服务端生成 em 标签；
不信任 ES 返回的 HTML。分词召回与字面高亮并非完全等价，部分命中可能没有高亮。

## 参数与失败语义

页码至少 1，每页 1～100，from+size 不超过 ES 默认窗口 10000。
关键词去首尾空白，最长 200 字符；空关键词返回规范空页。
类型仅允许 daily/activity/pet，排序仅允许 relevance/time/hot。
排序末尾增加 type、businessId，减少相同分数/时间造成的顺序漂移。
ES 显式请求精确候选总数。
ES IO 或服务异常返回现有 THIRD_PARTY_ERROR 业务错误，不伪装成成功的零结果。

## 尚存边界

total / totalPages 仍是 ES 候选数量，不是数据库二次过滤后的精确可见数量。
索引延迟时，一页可能不足 pageSize 或为空；本轮不跨页补扫或承诺精确总数。
新批准或新发布内容仍依赖原索引同步链路进入召回；
数据库回查可以阻止旧索引暴露已删/待审内容，但不能召回索引尚未收录的内容。
排序也仍依据 ES 数据，更新点赞后返回计数会更新，热度排序要等待索引同步。

下一批应单独收敛增量同步、失败反馈及全量重建，不与本次查询修改混在一起。

## 验证与交付（2026-10-25）

去重合计 20 个搜索相关测试，全部通过，未运行全量测试：
- 参数、候选过滤、顺序、去重与安全高亮。
- 三种内容的公开/本人预览/删除规则。
- 作者、点赞、收藏、报名的批量装配和同 ID 不同类型隔离。
- ES 请求构造、类型/归属过滤、稳定排序及失败语义。
- @Primary 服务接管和原 HTTP 请求/响应字段兼容。
- 2 个真实 MySQL 测试：当前内容/计数/互动状态、审核和删除后过滤。

先运行全部搜索测试；补齐测试库必填联系字段后，仅重跑受影响的 MySQL 测试类。
MySQL 使用已授权的本机 pet_system_test；每个用例独立事务，结束回滚，
不清空表、不修改现有业务记录。未访问线上 ES 或执行索引写入、重建。

选择器：
mvn -s target/report-build-settings.xml "-Dtest=*Search*Test" test

真实数据库测试需显式提供 PMS_SEARCH_MYSQL_TESTS=true、PMS_TEST_DB_PASSWORD。
本轮只提交源码、测试和说明，未部署。
