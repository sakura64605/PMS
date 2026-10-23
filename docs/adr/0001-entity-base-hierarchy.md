# 0001. 实体基类怎么分层

状态：已采纳（2026-10）

## 背景

项目有 31 个 `@TableName` 实体，**没有一个是公共基类的子类**。后果是：

- `createTime` / `updateTime` / `deleted` / `creator` 四个审计字段各写各的，多处缺失；
- `common/handler/MyMetaObjectHandler.java:23,29-30` 按**字段名**硬编码
  `"createTime"` / `"updateTime"` 做自动填充，于是 AI 模块五个用
  `created_at` / `updated_at` 的实体**从来没有被填充过**（静默失效，不报错）；
- 新增一张表就得把审计字段再抄一遍，且抄漏了也没有任何提示。

## 可选方案

### 方案 A：给所有老实体加 `implements Identifiable`
定义一个薄接口作为泛型下界，让 31 个老实体都实现它。

**否决**。Java 是名义类型 —— "实体有 `getId()`" 不等于"实体实现了 `Identifiable`"，
所以必须逐个改 31 个实体。而老实体一旦 `import` 重构层，
「删掉 `refactor/` 后老代码仍能独立编译」这条回滚底线就断了。

### 方案 B：单层抽象基类
所有实体继承一个带全部审计字段的基类。

**否决**。老表普遍没有 `creator` / `updater` / `deleted` / `version` 这四列
（例如 `pet_post` 连 `update_time` 之外的审计列都没有，`comment` 连 `update_time` 都没有），
硬套会让实体与物理表不匹配，查询直接报未知列。

### 方案 C：两层基类 + 放弃泛型上界（采纳）

```java
BaseEntity      // id + create_time + update_time          —— 映射老表的实体可用
  └ AuditEntity // + creator + updater + deleted + version —— 只有新表有这四列
```

同时把 `BaseService<E extends Identifiable>` 的**泛型上界去掉**。

## 决定

1. 分两层基类，按「物理表实际有哪些列」来划分，而不是按「概念上属于什么」。
2. 泛型不加 `Identifiable` 之类的上界。**当前没有任何泛型算法真的需要调用 `getId()`**，
   为想象中的需求强迫 31 个老实体改动，是拿确定的代价换不确定的收益。

## 后果

### 收益
- 映射老表的实体（如 `PmsPetPost`）**立刻**能继承 `BaseEntity` 复用主键与时间戳；
- 切到新表时，把 `extends BaseEntity` 改成 `extends AuditEntity` 即可获得完整审计能力，
  不需要重构调用方；
- 老实体一行未改，回滚底线保住。

### 代价
- 两层基类比单层多一个概念，新成员需要理解「为什么分两层」—— 这一条 ADR 就是答案；
- 实体继承 `BaseEntity` 后**不能**使用 `@TableLogic`：MyBatis-Plus 的逻辑删除会在所有
  自动生成的查询上强制追加 `deleted = 0`，而本系统有**回收站**、**恢复**、**彻底删除**
  三个功能都需要读到 `deleted = 1` 的行。因此 `deleted` 被当作普通列，由
  `AbstractBaseService.baseQuery()` 统一承担过滤责任，代价是每个查询都要自己带上它。

### 被否决方案的残留代价
老实体仍然没有基类，`MyMetaObjectHandler` 的按名字填充问题只在启用
`CoreMetaObjectHandler`（`pms.orm.meta-handler=core`）后才真正解决。
默认配置下行为与改造前完全一致，这是刻意的：这个改动影响全部 31 张表的插入，
必须能一键退回。

## 相关
- [0007 换表时机](0007-table-cutover-strategy.md)
- [0008 刻意不用模式的地方](0008-when-not-to-use-patterns.md)
