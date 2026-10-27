# 代码格式规范

统一规则在根目录 `.editorconfig`、`.prettierrc.json` 和 `.gitattributes`。

| 文件 | 工具 / 规则 |
|---|---|
| Java | google-java-format 1.22.0，AOSP四空格；保留import顺序、未使用import及长字符串原文，不重排Javadoc |
| React / TypeScript / CSS / HTML / JSON / YAML | 已有Prettier 3.9.10，二空格、双引号、分号、100列、LF |
| Python | Black 24.10.0，四空格、100列，单工作进程 |
| Maven POM和普通XML配置 | 四空格，保留属性、注释和非空文本；结构变化检查 |

仅处理Git跟踪的源码、测试和配置，以及新增格式化入口。不处理node_modules、构建产物、备份、本机私有配置、锁文件、Swagger快照及生成的依赖基线。MyBatis混合SQL/XML与CDATA不交给通用XML打印器，以保留原SQL文本；手工维护其缩进。

## 准备工具

```powershell
npm ci --prefix PMS-react
mvn -N org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy '-Dartifact=com.google.googlejavaformat:google-java-format:1.22.0:jar:all-deps' '-DoutputDirectory=target/format-tools'
python -m pip install --target target/format-tools/python black==24.10.0
```

如项目环境需要自定义Maven镜像，在Maven命令中加 `-s <settings文件>`。Python/YAML结构核验还需要PyYAML；本项目Python脚本已使用该依赖，缺少时安装到同一工具目录即可。Java格式器需要JDK17。所有临时工具在被Git忽略的target下，不进入运行包。

## 执行

```powershell
# 全部源码
python scripts/format_code.py
# 检查格式，不改文件
python scripts/format_code.py --check
# 按范围运行
python scripts/format_code.py --scope frontend
python scripts/format_code.py --scope backend --check
# 也可显式指定已有的Java格式器
python scripts/format_code.py --java-formatter <jar路径>
```

前端常用命令：`npm --prefix PMS-react run format` 和 `npm --prefix PMS-react run format:check`。

脚本格式化前后比较Python AST以及JSON、YAML、XML结构，不允许把排版操作变成配置值变更。初次全量整理还逐一比较了682份Java源码的AST（含标识符、修饰符及字符串值），结构差异为0。此批不重命名、不搬包、不修改接口和业务流程；格式化补充的配置、命令入口单独提交，源码排版另行提交。不为纯格式变化部署线上应用。

本次整理验证：前端构建与87项测试、后端全模块打包与33项相关测试、4项Python发布脚本测试通过；格式检查可重复通过。未运行全量数据库测试，未部署线上服务。
