# 高德公共地图能力：第一阶段

## 范围与当前状态

生产域名已支持 HTTPS。本阶段使用用户已有的 **Web端（JS API）Key 及对应安全密钥**，新增公共地图组件、Core 运行时配置接口和私有配置生成脚本。通过 JS API 支持地图展示、关键词搜索、点击选点、逆地理编码和用户主动定位。尚未连接真实高德凭据进行联调，也未部署本阶段业务产物。

活动地点和领养/救助地址旁增加“地图选点”。手动填写仍可用，不加载第三方脚本、不请求定位权限；只有打开地图后加载 SDK，只有点击定位后请求设备位置。

**当前只是辅助填写原地址字符串，不持久化坐标，也没有开启地理签到。** 公共组件可返回结构化 `MapPlace`，但业务表仍只有原地点字段；下一阶段需要明确坐标列、历史活动兼容、签到窗口、范围及定位精度规则，再增加后端校验。不能用本次前端选点代替可信签到证据。

## 分层与流程

```text
活动 / 领养救助地址输入框
  → LocationAddressField / LocationPicker
  → GET /pet-system/location/config（现有登录校验）
  → 动态加载高德 JS API 2.0
  → /_AMapService/（同源 Nginx 代理）
  → 固定高德服务地址
```

运行时配置由 Core 的 location 模块提供，默认关闭；开启时必须存在有效 JS API Key。返回仅包含 enabled、provider、coordinateSystem、jsApiKey 和固定 serviceHost。JS API Key 是浏览器可见的公开凭据，安全密钥不出现在响应、Core 配置或 React 构建产物。

浏览器仅设置 `_AMapSecurityConfig.serviceHost`，不设置明文 `securityJsCode`。Nginx 私有 include 在上游请求中注入安全密钥；此方式参考[高德 JS API 安全密钥官方说明](https://lbs.amap.com/api/javascript-api-v2/guide/abc/jscode)。使用[PlaceSearch](https://lbs.amap.com/api/javascript-api-v2/guide/services/autocomplete)进行地点搜索，使用[坐标转换](https://lbs.amap.com/api/javascript-api-v2/guide/transform/convertfrom)处理设备 GPS 坐标。

## 你需要填写的内容

工作区已准备 Git 忽略文件：`.local/location/amap.env`。仓库模板为 [amap.env.example](../../ops/systemd/amap.env.example)。仅替换两项：

```dotenv
PMS_AMAP_JS_API_KEY=你的Web端JS_API_Key
PMS_AMAP_JS_SECURITY_CODE=对应的安全密钥
```

不要在 Git 跟踪文件、聊天截图、前端 `.env` 或构建变量中放安全密钥。该私有文件不应提交；生成文件也留在 `.local/location/` 内。不要放进 `target` 或 `dist`：构建清理会删除它们。生成器接受安全字符，拒绝占位符、重复项和控制字符，不执行输入内容。

高德控制台需要确认该 Key 的服务平台是 Web端（JS API），并按控制台提供的有效域名设置配置 `www.petcircle.tech`；是否开放开发机域名应独立决定。以控制台当前权限、认证和配额为准，不假设创建 Key 就自动拥有所有服务权限。

已有的两项凭据足够先接 JS API。之后 AI 后端主动查附近医院、服务端地点搜索/逆地理编码，需要另申请 **Web服务 API Key**，不能把 JS API Key 混作服务端 Key。地理初筛与 24 小时实际接诊状态也不是同一个能力。

## 配置生成与部署位置

```powershell
python scripts/deploy/prepare_amap.py --env-file .local/location/amap.env --output-dir .local/location/generated
```

该命令只在本机生成文件，不联网、不部署、不打印凭据：

| 文件 | 用途 | 服务端建议位置 |
|---|---|---|
| core-amap.env | Core 开关与公开 JS API Key | `/opt/pms/config/amap.env` |
| 00-pms-amap-rate-limit.conf | Nginx http 级限流 zone | `/etc/nginx/conf.d/00-pms-amap-rate-limit.conf` |
| pms-amap.enabled.conf | 含安全密钥的 server 级代理 | `/etc/nginx/snippets/pms-amap.enabled.conf` |

最后一个文件必须 root 所有、权限 600，不能直接放入 conf.d，因为 location 必须在 server 内。在 HTTPS 站点的 server 内加入：

```nginx
include /etc/nginx/snippets/pms-amap.enabled.conf;
```

Core 的 systemd 独立 drop-in 可配置：

```ini
[Service]
EnvironmentFile=/opt/pms/config/amap.env
```

更新 Core/前端产物、安装代理和环境配置后，先 `nginx -t`，成功再 reload；修改 systemd drop-in 后 daemon-reload 并重启 Core。已有 Cloud、AI、XXL-JOB 等环境文件和 drop-in 必须保留，不覆盖整个服务单元。

代理只允许固定高德上游及当前使用的地点、逆地理编码、提示和坐标转换路径；绑定项目 JS API Key，每 IP 限速，禁转发站内 Cookie/Authorization，校验上游 TLS。不是任意 URL 转发接口。代理普通访问日志关闭，错误日志级别收紧，避免上游 URL 中的安全密钥进入常规日志。不要把完整 `nginx -T` 输出公开。

部署前应备份 Nginx 和新增 drop-in，确认真实 SDK 请求路径与权限；出现不在允许列表中的 SDK 请求时，先核对官方用途，再增加具体路径，不直接放开全部代理。

## 坐标、定位与交互

`MapPlace` 明确携带经纬度、GCJ02、AMAP、名称、地址和可选 poiId。经纬度校验有限数和范围，拒绝无效结果；设备定位经 `AMap.convertFrom(..., "gps", ...)` 转换后才用于高德地图。失败不偷偷用原 GPS 坐标顶替，也不伪造当前位置。

定位拒绝或不可用时可手动搜索。关闭弹窗销毁地图，异步结果通过请求序号和组件生命周期检查，旧逆编码结果不能覆盖较新的选点。SDK 加载失败支持重试，加载中复用同一 Promise，避免重复引入。当前选点不是距离校验，也不需要后端算出“签到成功”。

## 验证口径

本次本地测试覆盖：运行时配置默认关闭/错误配置失败/不返回安全密钥；SDK 单次加载与失败重试；手动输入不触发地图；坐标非法值；GPS 转换失败不降级；旧选点响应隔离；代理生成器校验与安全密钥去向。真实 Key、SDK 请求、配额和浏览器设备定位仍需配置后联调。

本地验证通过：地图前端6项、原编辑器相关1项、配置接口3项、模块布局与依赖边界3项、配置生成器2项，共15项；React构建和Core干净打包通过。无新增跨模块内部依赖，不访问数据库或付费模型。

地图属于可选增强能力。凭据缺失时原地址输入和原有发布接口继续使用；不修改历史记录、不执行数据库迁移、不创建健康档案或随访任务。
