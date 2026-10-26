# 我的活动报名

新增 React `/my-signups` 页面，对照提供的 `MySignupsView.tsx` 原型实现。入口在头像菜单、个人中心和首页活动区。保留暖色渐变头部、四张统计卡、搜索筛选栏、横向活动卡片、右侧报名信息、凭证弹窗和取消确认。

## API

- GET `/pet-system/activity/my-signups`：当前登录用户的报名分页。参数 `pageNum`、`pageSize`（上限100）、`keyword`、`status`、`signupStatus`、`timeRange`、`sort`。
- GET `/pet-system/activity/my-signups/summary`：不受列表筛选影响的个人统计 `total`、`pending`、`checkedIn`、`finished`。
- 取消继续调用 POST `/pet-system/activity/cancelSignUp/{activityId}`，成功后刷新列表和统计。

用户身份只取 UserContext，不接受客户端指定用户。搜索覆盖标题、地点、发起人昵称/用户名及自己的姓名/备注；筛选在数据库执行后分页，排序使用白名单并增加唯一ID作为稳定排序。`timeRange=upcoming` 仅包含尚未开始且公开可参加的活动；`past` 包含已结束记录。支持开始时间正/倒序、报名时间正/倒序和人数倒序。

每条返回 `activity`、自己的 `signup`、`checkInTime`、`available`、`canCancel`。保留报名/已签到/爽约状态1/3/4，排除历史取消状态2。现有取消业务物理删除报名记录，因此累计统计指当前保留的有效及历史参与记录，不声称包含已取消次数。删除、下架、审核中的活动记录仍可查看自己的报名信息，活动详情链接按可见性关闭。签到仍由发起人操作，参与者不能自行核销或撤销；凭证显示真实报名编号，不伪造可扫描二维码。原型无真实来源的“结识宠友40+”改为真实往期活动数。

不新增数据库字段。列表和统计均实时查询，不使用共享个性化响应缓存。沿用现有报名事务、取消截止时间和报名人数更新规则。

## 验证

`ActivityTakeoverMySqlTest` 新增个人报名查询及HTTP契约测试，使用本机 `pet_system_test` 的隔离数据并在结束后清理；覆盖归属、未登录、分页、筛选排序、不可见活动、历史记录和统计。React `background-refresh.test.mjs` 中 `my registrations` 测试覆盖凭证、筛选保留页面、取消刷新和不可见活动动作控制。完成 TypeScript/Vite 构建及模块边界检查。
