# 0002. 状态码要不要统一成一套枚举

状态：已采纳（2026-10）

## 背景

项目里同一列名 `status` 在不同表语义**相反**：

| 表 | `status = 0` 的含义 |
|---|---|
| `user` | 禁用 |
| `notice` | 草稿 |
| `comment` | 已删除 |
| `pet_post` | 待审核 |

代码里散落着 19 处 `switch`、数十处 `setStatus(1)` / `getStatus() == 2`。
已有 `common/enums/AuditStatus` 与 `modules/report/enums/ReportStatus` 两个枚举，
但业务代码基本不用 —— 例如 `ReportServiceImpl.java:232-234` 明明有 `ReportStatus`，
却手写 `if (status == 0) return "待处理"`。

## 可选方案

### 方案 A：设计一套统一的 `StatusEnum`
所有表共用一套码值，比如 `0 = 无效 / 1 = 正常 / 2 = 待审核` …

**否决**。强行统一意味着要重写老数据（`user` 里 `0` 是禁用，改成统一码后语义就变了），
而老表是禁改的。就算不改数据，也会出现"设 user 为 NORMAL"和"设 notice 为 PUBLISHED"
共用同一个数字的语义污染，读代码的人必须在心里做一次换算。

### 方案 B：每个限界上下文一个枚举，共享契约（采纳）

```java
public interface CodableEnum<C extends Serializable> extends IEnum<C> {
    C getCode();     // 落库的值
    String getDesc();// 展示文案
}
```

`PetPostStatus` / `NoticeStatus` / `UserStatus` / `CommentStatus` / `ActivityStatus` /
`SignupStatus` / `AuditStatus` 各自独立。

## 决定

**共享的是契约，不是取值。** 每个领域定义自己的枚举，取值严格照抄 DDL 注释
（不是照抄实体上的 javadoc —— 那里面有错的，见下）。

## 后果

### 收益
- 枚举实现 `IEnum` 后，MyBatis-Plus 会自动做枚举与数值的双向转换，
  新实体可以把字段直接声明成枚举类型，`setStatus(1)` 从类型层面消失；
- 展示文案统一由 `getDesc()` 提供 —— 前端原本在 200+ 处各自
  `switch (status)` 写中文，现在后端直接给出 `statusDesc`；
- 领域语义可以挂在枚举上：`PetPostStatus.isVisibleToPublic()`、
  `SignupStatus.isPunishable()`、`ActivityStatus.acceptsSignup()`，
  比散落的 `status == 4` 可读得多。

### 代价
- 老实体字段仍是 `Integer`（老代码禁改），所以枚举与整数在边界上必须来回转换。
  这是过渡期的必然成本，切表后实体字段可以改成枚举类型；
- 新增一个状态码要动枚举 + DDL 注释 + 前端文案。但相比原来"要动 DDL 注释 +
  若干处 `setStatus(n)` + AI 工具里的 switch + 前端多处 case"，已经收敛了很多。

### 调查中发现的事实
`PetPost` 实体的 javadoc 写着「状态：1-已发布 2-已领养/已完成 3-已下架」**只有三个值**，
而 DDL 注释写的是**六个值**（-1/0/1/2/3/4）。实体注释是错的，
且 `PetQueryRequestDto` 的注释也不完全准确。

**结论：这个项目的注释不能当契约读。** 所有枚举取值最终都回到 `pet-system.sql`
的建表语句逐条核对。这个错误的实体注释本次**没有修改**（保持老文件改动面最小），
但它是个真实的坑，应另行修正。

### 一个额外收获
`MessageService.getLikeTitle` 里有个 `case "系统通知"` 分支，而 `targetType`
实际只会是那几种互动目标 —— 那是个永远走不到、也永远测不到的死分支。
改用枚举作为目标类型后，这种"非法取值"在类型上就不可表达了，无需再写防御分支。

## 相关
- [0004 策略注册表取代 switch](0004-strategy-registry-over-switch.md)
