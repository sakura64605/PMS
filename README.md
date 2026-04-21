# 宠物管理系统 (PMS)

## 项目简介

宠物管理系统是一个专为宠物爱好者和宠物机构设计的综合管理平台，提供宠物信息管理、社区互动、活动组织、私信交流等功能。系统采用前后端分离架构，后端使用 Java Spring Boot 构建，前端使用 Vue 3 + TypeScript 开发。

## 技术栈

### 后端
- **语言**: Java 17
- **框架**: Spring Boot 3.x
- **ORM**: MyBatis-Plus
- **数据库**: MySQL
- **缓存**: Redis
- **消息队列**: RocketMQ
- **认证**: JWT
- **WebSocket**: 实时消息推送
- **分布式锁**: Redisson

### 前端
- **框架**: Vue 3
- **语言**: TypeScript
- **构建工具**: Vite
- **UI 组件库**: Element Plus
- **状态管理**: Vue 3 Composition API
- **网络请求**: Axios
- **WebSocket**: SockJS

## 项目结构

### 后端结构
```
src/main/java/com/hongjie/pms/
├── common/             # 通用组件
│   ├── annotation/     # 自定义注解
│   ├── aspect/         # 切面
│   ├── base/           # 基础类
│   ├── circuitbreaker/ # 熔断机制
│   ├── config/         # 配置类
│   ├── delay/          # 延迟任务
│   ├── enums/          # 枚举类
│   ├── exception/      # 异常处理
│   ├── filter/         # 过滤器
│   ├── handler/        # 处理器
│   ├── interceptor/    # 拦截器
│   ├── mq/             # 消息队列
│   ├── pojo/           # 通用对象
│   ├── punishment/     # 惩罚机制
│   ├── trace/          # 链路追踪
│   └── utils/          # 工具类
├── modules/            # 业务模块
│   ├── activity/       # 活动管理
│   ├── admin/          # 管理员功能
│   ├── audit/          # 审核功能
│   ├── comment/        # 评论功能
│   ├── feed/           # 动态 feed
│   ├── following/      # 关注功能
│   ├── like/           # 点赞功能
│   ├── message/        # 系统消息
│   ├── notice/         # 公告功能
│   ├── petpost/        # 宠物帖子
│   ├── privateMessage/ # 私信功能
│   └── user/           # 用户管理
└── PetSystemApplication.java # 应用入口
```

### 前端结构
```
PMS-vue/src/
├── api/            # API 接口
├── layouts/        # 布局组件
├── router/         # 路由配置
├── utils/          # 工具类
├── views/          # 页面组件
│   ├── audit/      # 审核页面
│   ├── dashboard/  # 仪表盘
│   ├── feed/       # 动态 feed
│   ├── login/      # 登录页面
│   ├── message/    # 消息中心
│   ├── notice/     # 公告页面
│   ├── pets/       # 宠物相关页面
│   ├── private-message/ # 私信页面
│   ├── profile/    # 个人中心
│   ├── recycle/    # 回收站
│   ├── register/   # 注册页面
│   ├── settings/   # 设置页面
│   └── user/       # 用户页面
├── App.vue         # 根组件
└── main.ts         # 应用入口
```

## 快速开始

### 后端启动
1. **配置数据库**: 确保 MySQL 数据库已启动，并创建名为 `pet_system` 的数据库
2. **修改配置**: 编辑 `src/main/resources/application.yaml` 文件，配置数据库连接信息
3. **运行应用**: 执行 `PetSystemApplication.java` 启动后端服务

### 前端启动
1. **安装依赖**: 进入 `PMS-vue` 目录，执行 `npm install` 安装依赖
2. **启动开发服务器**: 执行 `npm run dev` 启动前端开发服务器
3. **访问应用**: 打开浏览器，访问 `http://localhost:5173`

## 主要功能

### 1. 用户管理
- 用户注册、登录、登出
- 个人资料管理
- 头像上传
- 密码修改

### 2. 宠物管理
- 发布宠物信息
- 编辑宠物信息
- 查看宠物详情
- 收藏宠物

### 3. 社区互动
- 点赞、评论
- 关注用户
- 动态 feed
- 私信功能

### 4. 活动管理
- 发布活动
- 报名活动
- 活动详情

### 5. 系统管理
- 公告管理
- 审核管理
- 回收站

## 开发指南

### 后端开发
1. **添加新模块**: 在 `modules` 目录下创建新的业务模块
2. **添加 API**: 在对应模块的 `controller` 包中添加新的 API 接口
3. **添加服务**: 在对应模块的 `service` 包中实现业务逻辑
4. **添加数据访问**: 在对应模块的 `mapper` 包中添加数据访问接口

### 前端开发
1. **添加新页面**: 在 `views` 目录下创建新的页面组件
2. **添加路由**: 在 `router/index.ts` 文件中添加新的路由配置
3. **添加 API 调用**: 在 `api` 目录下添加新的 API 接口调用
4. **添加组件**: 在需要的地方添加新的组件

## 部署说明

### 后端部署
1. **构建项目**: 执行 `mvn clean package` 构建项目
2. **部署应用**: 将生成的 `jar` 文件部署到服务器
3. **启动服务**: 执行 `java -jar pms.jar` 启动服务

### 前端部署
1. **构建项目**: 进入 `PMS-vue` 目录，执行 `npm run build` 构建项目
2. **部署应用**: 将生成的 `dist` 目录部署到 Web 服务器

## 注意事项

1. **数据库配置**: 确保数据库连接信息正确配置
2. **Redis 配置**: 确保 Redis 服务已启动并正确配置
3. **RocketMQ 配置**: 确保 RocketMQ 服务已启动并正确配置
4. **WebSocket 配置**: 确保 WebSocket 服务已正确配置
5. **CORS 配置**: 确保 CORS 配置正确，允许前端访问

## 高并发优化

1. **分布式锁**: 使用 Redisson 实现分布式锁，确保并发操作的原子性
2. **缓存机制**: 使用 Redis 缓存热点数据，提高查询性能
3. **消息队列**: 使用 RocketMQ 实现异步处理，提高系统吞吐量
4. **限流机制**: 实现基于 Redis 的限流，防止高并发攻击
5. **熔断机制**: 实现服务熔断，提高系统稳定性

## 缓存与数据库一致性

1. **缓存更新机制**: 使用消息队列实现缓存更新，确保缓存与数据库一致性
2. **缓存过期策略**: 根据业务场景设置合理的缓存过期时间
3. **缓存预热**: 实现缓存预热机制，提高系统启动速度
4. **缓存监控**: 添加缓存监控，及时发现缓存问题

## 安全措施

1. **输入验证**: 加强输入验证，防止 XSS 攻击和 SQL 注入
2. **权限控制**: 完善权限控制，确保用户只能访问自己的资源
3. **密码加密**: 使用 BCrypt 加密密码，提高安全性
4. **JWT 认证**: 使用 JWT 实现无状态认证，提高系统可扩展性
5. **日志记录**: 添加详细的日志记录，便于问题排查

## 联系方式

- **项目地址**: https://github.com/hongjie/pet-management-system
- **开发者**: Hong Jie
- **邮箱**: hongjie@example.com
