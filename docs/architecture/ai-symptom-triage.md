# AI 症状分诊工具

## 本次增加什么

新增 `triage_pet_symptoms`，部署于 AI 服务，通过现有 BaseTool → ToolRegistry → ReAct ToolExecutor 注册。首版范围为**猫呕吐的紧急程度初筛**，不输出疾病诊断。无需新增公开 HTTP 接口或数据库表，现有聊天与工具轨迹返回结构继续使用。

判断逻辑由独立 `PetSymptomTriagePolicy` 执行，是可复现、可单测的规则；LLM 负责从多轮聊天提取明确事实、追问和解释，不能自己改写工具等级。未填写的布尔字段视为未知，不是 false。每轮新信息重新评估，保留最高优先级的已知危险事实。

## 输出与决策

| level | 意义 | Agent 后续 |
|---|---|---|
| RED | 已知危险征象 | 首先建议立即联系/前往兽医急诊，不等追问或 RAG |
| YELLOW | 已知需要尽快评估的因素 | 建议尽快联系兽医、当天评估；可查询就医前观察知识 |
| GREEN | 完整输入未触发当前升级规则 | 说明不是确诊安全，查询观察知识并说明升级条件 |
| UNDETERMINED | 信息不足、无效或规则不适用 | 追问或建议兽医评估，不默认绿色 |

返回包含 `ruleVersion`、`status`、`informationComplete`、`reasons`、`missingFields`、`invalidFields`、`followUpQuestions`、`recommendedAction`、`advice`、`knowledgeQuery`、`limitation` 和来源链接。

`YELLOW + NEEDS_INFORMATION` 表示已知因素已经要求就医，但仍需补充信息排除更高紧急程度；不是等全部回答后再联系兽医。RED 的 followUpQuestions 为空，避免延迟就医。规则不覆盖的物种/症状返回 UNSUPPORTED_SCOPE；已知危险征象仍优先提醒就医。

## 输入

- 基本信息：`species`（cat/dog/other）、`symptom`（vomiting/other）、`ageMonths`。
- 呕吐：`vomitingCount24h`、`durationHours`、`vomitAppearance`（food/liquid/foam/hairball/bile/blood/coffee_ground/other）。
- 全身情况：`mentalState`（normal/lethargic/severely_weak）、`appetite`（normal/reduced/none）、`waterRetention`（normal/reduced/unable_to_keep_down）。
- 伴随情况：`diarrhea`、`recurrentVomiting`、`knownMedicalCondition`。
- 危险筛查：`bloodInVomit`、`suspectedToxin`、`suspectedForeignBody`、`breathingDifficulty`、`collapseOrSeizure`、`severeAbdominalPain`、`abdominalSwelling`、`unableToUrinate`、`severeOrContinuousVomiting`。

所有布尔项只接受真实 boolean；数值不接受字符串、负值、溢出或小数。各自范围见工具参数描述。已知红旗不会被无关字段错误掩盖。声明正常/否定必须来自用户，模型不能根据“只吐了一次”补全所有阴性事实。

绿色需要全部基本情况与危险筛查完成。追问分组优先筛查危险，再问年龄、次数、性状、食欲和既往情况；每轮最多三组，后续回答再调用工具。规则引擎不跨轮保存病历，事实来自现有聊天上下文，因此上下文被清理时应重新确认，不能假装记得完整健康档案。

## 示例

用户：“我家猫吐了，怎么办？”

```json
{"species":"cat","symptom":"vomiting"}
```

返回 UNDETERMINED，首先确认危险征象、年龄和频率/性状等。若随后用户说“有血”，即可调用：

```json
{"species":"cat","symptom":"vomiting","bloodInVomit":true}
```

返回 RED，不因其余信息缺失而继续等待问答。模型应先建议立即联系急诊，不先搜索普通护理知识。

## 规则依据与适用边界

参考 [Cornell 猫呕吐说明](https://www.vet.cornell.edu/departments-centers-and-institutes/cornell-feline-health-center/health-information/feline-health-topics/vomiting)、[Merck 猫呕吐说明](https://www.merckvetmanual.com/cat-owners/digestive-disorders-of-cats/vomiting-in-cats)及 [VCA 猫急症说明](https://vcahospitals.com/know-your-pet/emergencies-in-cats)，检索日期 2026-10-27。

其中关于反复呕吐、精神/食欲改变、腹泻及危险征象的描述用于制定保守分流。**红黄绿是项目工程规则，不是上述机构认证的量表**；年龄小于6月/至少120月、24小时内至少2次、持续至少24小时等是当前保守升级阈值，不是确诊或排除疾病的临床边界。正式作为医疗分诊产品前需要执业兽医审阅和真实案例评估。

不提供药物剂量、催吐或自行禁食方案。不通过“吐毛球”直接断言无害。知识库资料只能辅助，不能覆盖更高等级或未知信息。

## 与完整闭环的区别

本次完成**分诊工具与 Agent 编排指引**，未增加医院地图供应商、实时营业验证、健康档案或随访任务模型。Agent 被明确禁止编造医院地址/24h 状态，或声称已写档案、已设置提醒。红色情况直接建议联系可接诊兽医；后续接入医院工具需要城市/位置、真实数据来源和接诊确认。

完整流程还需独立设计医院查询、经过确认的健康记录、随访时间/时区及幂等写入。这些不能用长期记忆文本和当前 delay_task 随意冒充。当前聊天仍按既有会话规则保存消息与工具轨迹，不额外自动写健康档案。

## 验证与发布

相关测试覆盖信息缺失、每个红旗、未知/错误类型、年龄和时间边界、绿色所需完整事实、黄色不被缺失信息降级、其他症状不套用规则，以及真实注册表的工具 schema/executor JSON 观察值。不调用付费模型、不依赖数据库。

2026-10-27 本地验证：新增分诊测试19项，原工具注册1项、会话6项、传输4项，共30项通过；AI服务及依赖模块打包通过。使用本机现有镜像配置离线执行：

```powershell
mvn -s target/report-build-settings.xml -o -pl services/pms-ai-service -am test '-Dtest=PetSymptomTriageTest,ToolRegistrationTest,AiConversationTest,AiTransportTest' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn -s target/report-build-settings.xml -o -pl services/pms-ai-service -am package -DskipTests
```

`target/report-build-settings.xml` 是本机临时配置，不是仓库文件；其他环境用自己的Maven配置。此次没有发布服务器。

工具调用与多轮追问仍由模型执行，单元测试不等于真实模型必然遵守流程；上线前应使用聊天验收“只说猫吐了”“补充吐血”“信息不全要求直接判绿色”等场景。此次改动仅需更新 AI 服务产物；没有数据库迁移、前端修改或 Core 协议变化。
