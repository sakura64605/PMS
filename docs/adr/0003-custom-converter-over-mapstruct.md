# 0003. DTO 转换用 MapStruct 还是自研

状态：已采纳（2026-10）

## 背景

全项目共有 353 处 `CommonResult` 引用，但 `BeanUtils.copyProperties` 用了 **0 次**，
pom 里也没有 MapStruct。所有实体到 DTO 的转换都是手写 Lombok `@Builder` 逐字段装配。
重复最严重的是 `PetPostServiceImpl` 里同一段 `PetListResponseDto.builder()...build()`
**抄了 5 遍**。

## 可选方案

### 方案 A：引入 MapStruct
编译期生成映射代码，零反射开销，业界常用。

**否决**，三个理由：

1. **自动映射会静默改变 API 响应。** 本项目对外的硬约束是"返回字段不能变"。
   MapStruct 会把实体**新增的字段**自动映射进 DTO —— 也就是说，某天有人给
   `PmsPetPost` 加一个 `internalRemark` 字段，API 响应里就会凭空多出这个字段，
   编译不报错、测试不报错，只有前端或者安全审计会发现。手写转换器是**显式白名单**，
   加字段必须改代码，是负担也是保护。
2. **老 DTO 的装配不是纯 1:1。** `PetListResponseDto` 需要聚合作者信息、
   收藏态、审核态；`CommentRespDto` 需要组装整棵子树。MapStruct 这部分仍要写
   `@AfterMapping` 或 expression，收益被吃掉大半。
3. **构建成本对当前项目不划算。** pom 的 `annotationProcessorPaths` 目前只有 Lombok，
   加 MapStruct 要同时配置 `lombok-mapstruct-binding`（两者都在注解处理期改 AST，
   顺序不对会互相覆盖）、IDE 插件。而项目只有 1 个测试，引入这类
   "配置错了很难察觉"的构建依赖属于增加脆弱点。

### 方案 B：自研泛型转换器（采纳）

```java
public interface EntityConverter<S, T> {
    T toView(S source);
    default List<T> toViews(List<S> sources) { ... }
    default PageResult<T> toPage(PageResult<S> source) { ... }
}
```

## 决定

自研 `EntityConverter`，每个模块一个 `@Component` 转换器，继承得到列表/分页的默认实现。

## 后果

### 收益
- 出参是显式白名单，实体加字段不会泄漏到 API；
- 转换器是纯函数，单测一行一个断言，不需要 Spring 上下文、不需要数据库 ——
  `PetPostDtoConverterTest`（8 个用例）与 `CommentDtoConverterTest`（9 个用例）
  就是这么写的；
- 重复装配收敛到一处：原来 `PetPostServiceImpl` 里 5 份 `builder()` 现在只有一份；
- 将来某个模块确实需要 MapStruct 时，`EntityConverter` 接口不变，实现类可替换。

### 代价
- 字段多的时候手写映射冗长，且**漏字段不会有编译错误** ——
  实测中这确实是最容易出错的地方（`petBreed → petType` 这类改名映射写错，
  表现是前端拿到空值而不是报错）。缓解手段是为转换器写逐字段断言的单测，
  上面提到的两个测试类正是为此存在；
- 没有 MapStruct 的 `@Mapping` 声明式校验。

### 一个不得不偏离接口的地方
`CommentDtoConverter` **没有**实现 `EntityConverter`。因为评论树的节点转换需要上下文
（子评论分组、作者表），而 `EntityConverter` 的契约是"一个源对象映射成一个目标对象"。
与其为了套接口把上下文塞成转换器的成员变量（会让它变成有状态、不再可安全复用），
不如明确声明 `toView(Comment, CommentTreeContext)`。
**接口是用来解决问题的，不是用来凑数的** —— 这是接口隔离原则的一次实际应用。

## 相关
- [0004 策略注册表取代 switch](0004-strategy-registry-over-switch.md)
- [0008 刻意不用模式的地方](0008-when-not-to-use-patterns.md)
