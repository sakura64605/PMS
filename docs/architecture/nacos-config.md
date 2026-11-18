# Nacos 配置中心迁移

Core、Gateway、Search、AI 的 cloud profile 通过 Spring ConfigData 加载 Nacos。Spring Cloud Alibaba 2025.0.0.0 对应当前 Boot 3.5 / Cloud 2025.0。注册发现同时使用 Nacos Discovery，所有服务使用相同 namespace 与 group。

## Nacos 配置

准备独立 Nacos 服务，客户端地址默认 127.0.0.1:8848。确保客户端可访问 Nacos HTTP 端口及相应 gRPC 端口。部署时按 Nacos 官方说明配置鉴权和持久化。

将 config/cloud 下五份 YAML 分别发布为同名 Data ID，Group 默认 PMS，namespace 默认 public（空 ID）。包括 application-cloud.yaml、pms-core-service-cloud.yaml、pms-gateway-cloud.yaml、pms-search-service-cloud.yaml、pms-ai-service-cloud.yaml。先导入公共配置，再导入服务配置，服务配置覆盖公共配置。导入为必需项，启动时缺失配置会失败。

各服务配置 PMS_NACOS_SERVER_ADDR、PMS_NACOS_NAMESPACE（填写 namespace ID）、PMS_NACOS_GROUP、PMS_NACOS_USERNAME、PMS_NACOS_PASSWORD。凭据由环境变量提供。非 cloud profile 默认禁用 Nacos。

启用 cloud profile 前先发布所有 Data ID。原 PMS_CONFIG_URL 已不被这些客户端使用。现有服务器发布脚本包含旧配置中心部署编排，切换上线前需要调整对应环境文件、启动依赖及发布编排。Eureka 注册中心源码与 Maven 模块已移除。旧 pms-config-server 模块暂留作回滚参考。

## 验证

先构建四类服务，再使用独立测试 namespace 启动，核对公共及服务配置的覆盖顺序。修改非敏感配置后检查刷新事件与目标 Bean 实际值。refreshEnabled=true 表示订阅变更，构造时绑定的字段、连接池和部分基础组件仍需按自身刷新能力处理，不能假设所有配置自动热更新。

此改造未自动部署生产 Nacos，也未切换线上服务。生产切换需要先准备 Nacos、导入 Data ID 并调整服务器发布编排。

## 服务发现

cloud profile 启用 Nacos Discovery，本地非 cloud 默认禁用。PMS_SERVICE_IP 默认 127.0.0.1，跨主机部署时必须填写其他服务可达的地址。注册与配置共用 PMS_NACOS_SERVER_ADDR、PMS_NACOS_NAMESPACE、PMS_NACOS_GROUP 和鉴权参数。Gateway 的 lb:// 路由、Feign 服务名沿用既有名称。双搜索实例通过不同端口区分。

本地 cloud_stack.py 改为连接预先准备的 Nacos。旧 deploy_cloud.py 是首次 Eureka/Config Server 联合部署脚本，现已拒绝执行，生产迁移需要重新编排。verify_cloud.py 检查两个搜索实例健康及网关实际负载分布，注册列表和配置刷新仍需连接真实 Nacos 验证。
