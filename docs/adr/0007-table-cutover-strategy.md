# 0007. 什么时候换物理表

状态：已采纳（2026-10）

## 背景

重构的目标之一是把数据模型换掉：让逻辑删除与业务状态解耦、把字段名改对、
补上乐观锁。为此已经写好了新表结构（`src/main/resources/db/pms-pet-post.sql`）。

但在准备接线时发现一个**当初没有预料到的问题**：`pet_post` 不是 petpost 模块的私产。

读取它的模块至少有：

| 模块 | 用途 |
|---|---|
| `comment` | 自增 `comment_count`、查帖子作者发通知 |
| `like` / `favorite` | 点赞/收藏时改计数、查帖子 |
| `report` | 举报详情里展示被举报内容 |
| `audit` | 审核链路读帖子内容与状态 |
| `feed` | `user_inbox` 里存 `post_id`，并冗余发布者信息 |
| `search` | ES 同步时读帖子 |
| `statistics` | 统计新增帖子数等 |

**这些模块都跑在老代码上，不会跟着一起变。** 如果只有 petpost 的新实现改去写
`pms_pet_post`，会产生一批"不报错、只是结果不对"的静默不一致：

| 场景 | 后果 |
|---|---|
| 有人评论一条新帖 | 老 `CommentServiceImpl` 给老表自增 —— 新帖在老表不存在，**UPDATE 影响 0 行且不报错**；随后 `selectById` 拿不到帖子 → **通知不发** |
| 管理员审核新帖 | 审核链路读老表 → **找不到帖子** |
| 新帖进 ES | `SearchDataSyncService` 读老表 → **搜不到** |
| 统计报表 | 数老表 → **数字虚低** |

用一句话概括：**只要还有一个模块读老表，单独换它引用的表就会制造数据分叉。**

## 可选方案

### 方案 A：双写过渡
新实现写新表的同时，把变更镜像一份到老表。

**否决（暂缓）**。能让所有老模块继续正常工作，过渡期数据一致、可随时回滚。
代价是双写逻辑本身与它的漏写风险 —— 而本项目没有回归测试能兜住"漏写"，
静默的镜像遗漏比不做还危险。

### 方案 B：整个"帖子簇"一次性迁移
把 petpost/comment/like/favorite/audit/report/feed/search 作为一个整体同时换。

**否决（本轮不做）**。一次到位、没有过渡期，但批次巨大、风险集中在一次变更里，
而**本机跑不起应用，无法验证**。

### 方案 C：先重写实现、保持表不变，整个簇完成后再统一切表（采纳）

标准做法叫 expand → migrate → contract：
1. **expand**：新结构就位（SQL 已写好），老结构继续服务；
2. **migrate**：逐个模块替换实现，读写仍是老表，行为完全不变、可随时回滚；
3. **contract**：全部消费者都换成新实现后，一次性切换表并做数据迁移。

## 决定

**本轮不换表。** 新实现仍读写老表；`pms-pet-post.sql` 作为切表时的准备件保留，
其中已经标注了切表要改的每一处。

## 后果

### 收益
- 迁移期**没有任何数据分叉**：审核、ES、统计、feed、评论通知全部照常工作；
- 每一步都可以独立回滚，而且回滚只是"删 `refactor/` 里的一个模块"；
- 实现层的架构改进（统一基类、枚举、策略族、事件）**不依赖换表**就能落地 ——
  这两件事本来就不必捆绑。

### 代价
- 改进后的表结构（逻辑删除与状态解耦、`pet_breed` 改名、`pet_age_months`、
  `version` 乐观锁）暂时用不上，实体仍要迁就老表的列名
  （`@TableField("pet_age")` / `@TableField("pet_type")`）；
- 切表时还有一波改动要做，不算真正完成。

### 切表时要做的三件事（已写进代码注释）
以 `PmsPetPost` 为例：

1. 实体改 `@TableName("pms_pet_post")`、去掉列名映射、
   并从 `extends BaseEntity` 改为 `extends AuditEntity`（获得
   creator/updater/deleted/version）；
2. `PmsPetPostMapper` 里每条 SQL 的表名换成 `pms_pet_post`，补上 `AND deleted = 0`；
3. **唯一涉及业务语义的改动**：把"逻辑删除 = 将 `status` 置为 -1"
   改为"设置 `deleted = 1`"（`PmsPetPostServiceImpl` 中标了「切表点」注释）。

第 3 条之所以单独强调，是因为它是整个切表过程中唯一不能靠改表名/列名机械完成的部分 ——
其余都是纯映射，只有这一处改变了语义。

### 迁移脚本的两个要点
- **保留原主键**：`comment.target_id`、`like_record.target_id`、
  `favorite_record.target_id`、`user_inbox` 存的全是 `pet_post.id`。
  如果重新生成 id，这些关联会**全部指向错误的帖子**。
- **`ON DUPLICATE KEY UPDATE`**：迁移脚本可重复执行，
  切换前可以再跑一次把增量数据同步过去。

## 相关
- [0006 重构隔离与回滚](0006-refactor-isolation-rollback.md)
- [0001 实体基类怎么分层](0001-entity-base-hierarchy.md)
