# AI 测试闭环实践台

页面化实践以下完整流程：

```text
原始需求
  -> requirement-to-tad（大模型）
  -> TAD/TRD/STD
  -> tad-to-test-yaml（大模型）
  -> 功能测试用例 YAML
  -> api-test-automation-workflow（大模型）
  -> 接口自动化 generation-spec
  -> 脚手架 JAR
  -> Java + Suite YAML + JSON5 + Manifest + 二次加工计划
  -> Maven 编译 / 目标单例
  -> 结构化反馈报告（大模型）
```

## 两种模式

### GitHub Pages 演练模式

直接在浏览器练习完整流程，不需要安装 Java、Maven 或配置 API Key。

- TAD、功能用例、接口规格和报告使用内置演练结果。
- 脚手架生成、Manifest 校验和 Maven 编译显示模拟门禁结果。
- 不读取访问者电脑上的文件。
- 不执行 JAR、Maven 或真实接口。
- “运行单例”保持禁用。

### 本地真实模式

本地服务调用真实 Skill、大模型、生成器 JAR、目标项目和 Maven。

- 大模型 API Key 只保存在环境变量中，不进入页面和 Git。
- 默认把目标项目克隆到临时目录，避免污染业务工作树。
- 可以生成真实脚手架、执行测试编译和目标单例。
- 真实单例运行前必须在页面显式确认。

## 本地准备

需要：

- Python 3.11+
- Java 11+
- Maven 3.8+
- 一个 Maven 接口自动化测试项目
- 可选：OpenAI 或兼容 Chat Completions 协议的大模型 API

生成器 JAR 已在仓库中：

```text
dist/api-test-scaffold-generator.jar
```

## 配置大模型

默认使用 OpenAI 兼容的 `POST /v1/chat/completions` 协议。

```bash
export LLM_API_KEY="你的 API Key"
export LLM_BASE_URL="https://api.openai.com/v1"
export LLM_MODEL="gpt-5-mini"
```

使用其他兼容服务时，只需要替换 `LLM_BASE_URL` 和 `LLM_MODEL`。不要把 API Key 写入仓库、README、页面输入框或截图。

未配置 `LLM_API_KEY` 时，本地页面仍可使用脚手架生成、校验和 Maven 功能，但点击 AI 生成按钮会显示配置提示。

## 启动

从仓库根目录运行：

```bash
python3 tools/api-test-workflow-lab/server.py
```

打开：

```text
http://127.0.0.1:8765
```

不自动打开浏览器：

```bash
python3 tools/api-test-workflow-lab/server.py --no-open
```

端口被占用时：

```bash
python3 tools/api-test-workflow-lab/server.py --port 8766
```

## 从零实践

### 1. 输入需求

选择顶部文档页签“需求”，输入 PRD、用户故事、规则、流程、角色、枚举、异常和接口线索。

首次练习可点击“载入完整 Demo”，使用记录创建需求。

点击“生成 TAD”。页面调用 `$requirement-to-tad` 的规则，输出：

- 业务背景
- TRD 业务测试分析
- STD 系统与接口测试分析
- 可验证规则编号
- 待确认项
- 需求到规则的追溯关系

### 2. TAD 转功能用例

切换到“TAD”页签，检查并修改分析结果，然后点击“生成用例”。

页面调用 `$tad-to-test-yaml`，输出 `TestCases` YAML。接口自动化候选用例的 `tags` 应为 `接口` 或 `服务端`。

重点检查：

- `caseId` 唯一且稳定
- `thought` 能追溯到 TRD/STD
- P0 覆盖主流程、阻断规则和关键枚举
- 反向用例保留非法值、空值和边界值

### 3. 功能用例转接口规格

切换到“用例”页签，点击“生成接口规格”。

页面调用 `$api-test-automation-workflow`，生成 `generation-spec.yaml`。这一步默认执行首次智能加工：

- 固定值直接保留
- 明确枚举写入 `_enum` 和 `fieldHints`
- 易失业务 ID 转成 `__DATA_REF:alias.output__`
- 唯一字段标记运行时生成
- 环境基础数据标记查询来源
- 需要造数时生成 `preparationLines`
- 不改写反向用例的非法输入
- 不放宽业务期望

Java 类型和框架类必须来自目标项目证据。演练模式使用 `com.example` 占位类型；真实项目中应在生成前创建实践会话，让大模型获得框架证据。

### 4. 创建实践会话

本地模式填写“测试项目目录”。

建议保持“临时副本”开启：

- Git 项目使用 `git clone --local` 创建隔离副本。
- 非 Git 项目复制到系统临时目录。
- 原项目不会写入生成文件。

点击“检查”查看测试类、Suite YAML、JSON5、API 类和数据准备器数量。

点击“开始实践”创建工作目录。关闭“临时副本”会直接修改原目录，应先确认工作树状态。

### 5. 生成接口自动化脚手架

在“接口规格”页签点击“生成脚手架”。生成器输出：

```text
src/test/java/<package>/<ClassName>.java
src/test/resources/<package>/<dataKey>.yaml
src/test/resources/<package>/<case>_req_<env>.json5
src/test/resources/<package>/<case>_exp_<env>.json5
.api-test-generator/<apiCode>/scaffold-manifest.yaml
.api-test-generator/<apiCode>/executableization-plan.yaml
```

随后点击“严格校验”。初次生成要求：

```text
Missing: 0
Modified: 0
Malformed: 0
```

### 6. 保留的二次加工入口

切换到“加工计划”页签查看 `executableization-plan.yaml`。

即使首次生成已经处理动态数据，计划仍保留每个 `fieldHints` 的 `decision: pending`，用于人工或 `$make-api-tests-executable` 再次校准：

- 确认真实枚举映射
- 接入实际 databuilder
- 校准环境查询
- 调整账号和鉴权
- 替换演练占位类型
- 验证上传文件和依赖资源

二次加工后使用非严格 Manifest 校验。`Modified` 可以大于 0，但 `Missing` 和 `Malformed` 必须为 0。

### 7. 编译与测试

点击“测试编译”，后台执行：

```bash
mvn -DskipTests test-compile
```

真实运行前填写测试类名并勾选：

```text
允许创建测试数据并调用真实接口
```

点击“运行单例”，后台只执行指定类：

```bash
mvn -Dtest=<ClassName> test
```

页面不接受任意 Shell 命令。运行失败时应按生成、编译、启动、鉴权、网络、数据准备、业务响应和字段断言分类。

### 8. 生成报告

编译或单例完成后页面自动切换到“报告”。点击“生成报告”，页面调用接口反馈 Skill，输出：

- `passed`、`failed`、`blocked` 或 `skipped`
- 失败阶段和根因
- Manifest、日志和报告证据
- 首次/二次加工变更
- 上游 TAD/YAML 反馈
- 黄金样例资格

产品缺陷和环境问题不得通过修改正确期望来处理。

## GitHub Pages 部署

仓库包含 `.github/workflows/pages.yml`。仓库所有者需要在：

```text
Settings -> Pages -> Build and deployment -> Source
```

选择 `GitHub Actions`。之后推送 `main` 或手动运行 `Deploy workflow lab to GitHub Pages`。

地址格式：

```text
https://<github-user>.github.io/<repository>/
```

## 测试

```bash
python3 -m unittest discover -s tools/api-test-workflow-lab/tests -v
node --check tools/api-test-workflow-lab/web/app.js
mvn -f tools/api-test-scaffold-generator/pom.xml test
```

## 安全边界

- 服务只监听 `127.0.0.1`。
- API Key 只从环境变量读取。
- 页面不返回或显示 API Key。
- 默认操作临时副本。
- 不提供任意命令执行接口。
- 测试类名经过白名单格式校验。
- 在线演练模式永远不调用真实接口。
