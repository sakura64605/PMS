# 我的报名页面发布

2026-10-26 19:40（北京时间）部署源版本 `79e2da5`。

页面：http://111.229.254.252/my-signups 。也可从头像菜单、个人中心、首页活动区进入。

发布目录 `/opt/pms/releases/my-signups-79e2da5-20261026`，后端 SHA256 `fbaf9b5b453c6b9f1e3f9ae15790958953addf11f16d46882a30e9b1e4105ec1`。替换核心后端并重启 pms，验证成功后复制前端资源并原子切换 index.html。数据库没有变化，原五份应用配置逐项比对一致，其他微服务配置保持原样。

线上检查通过：登录后的报名列表和统计接口、用户归属、匿名访问拒绝；公网页面入口与构建一致，三个入口资源返回成功，六个服务均 active。未代替用户进行浏览器视觉验收。

回滚备份在发布目录：`backend-before.jar`、`index-before.html`、`current-before.json`。原版本为 `0244f08`。验证结果见服务器 `api-verification.json`、`final-verification.json`、`deployment.json`。
