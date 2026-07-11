# 需求到 UI/接口自动化中文 Skills

这是一组从知识库和现有 Put 自动化框架中提炼的通用测试 Skills，覆盖“需求结构化分析 -> YAML 测试用例 -> Web UI ATC 或接口自动化 -> 执行诊断与反馈沉淀”完整闭环。Skill 的机器标识使用英文小写和连字符，面向使用者的说明、规则和默认提示词均已中文化。

## 目录

```text
skills/
├── requirement-to-tad/          # 原始需求 -> TAD/TRD/STD
├── tad-to-test-yaml/            # TAD/TRD/STD -> YAML 测试用例
├── test-yaml-to-web-atc/        # 功能 YAML -> Web ATC
├── close-atc-feedback-loop/      # ATC 执行 -> 诊断、修复、复跑与反馈
├── api-test-automation-workflow/ # 接口 YAML -> Put 脚手架 -> 可执行化与反馈
└── requirement-to-atc-workflow/ # UI/接口双通道端到端编排

tools/
└── api-test-scaffold-generator/ # 脚手架生成器 Maven 源码

dist/
└── api-test-scaffold-generator.jar # 可独立执行的 fat JAR
```

每个 `skills` 子目录都可独立安装。`api-test-automation-workflow` 已内置相同 JAR，因此只安装该 Skill 也能执行生成流程。

## 安装

将需要的 Skill 目录放入 Codex Skills 目录：

```bash
mkdir -p ~/.codex/skills
cp -R skills/requirement-to-tad ~/.codex/skills/
cp -R skills/tad-to-test-yaml ~/.codex/skills/
cp -R skills/test-yaml-to-web-atc ~/.codex/skills/
cp -R skills/close-atc-feedback-loop ~/.codex/skills/
cp -R skills/api-test-automation-workflow ~/.codex/skills/
cp -R skills/requirement-to-atc-workflow ~/.codex/skills/
```

重新打开 Codex 任务后即可按 Skill 名称调用。

## 分阶段实践

```text
使用 $requirement-to-tad，将这份 PRD 转换为结构化 TAD/TRD/STD 测试分析，并保留待确认项。
```

```text
使用 $tad-to-test-yaml，基于这份 TAD 分析生成 YAML 测试用例，要求 thought 可追溯到规则来源。
```

```text
使用 $test-yaml-to-web-atc，基于功能 YAML、已跑通 ATC 和真实页面证据，生成一条稳定的 P0 保存主流程。
```

```text
使用 $close-atc-feedback-loop，分析这次 ATC 执行日志，完成根因分类、最小修复、单例复跑、黄金脚本沉淀和上游反馈。
```

```text
使用 $api-test-automation-workflow，读取目标 Put 项目和接口 YAML，生成接口自动化脚手架，完成运行时数据二次加工、测试编译、单例执行和反馈回写。
```

生成器也可独立使用：

```bash
java -jar dist/api-test-scaffold-generator.jar example
java -jar dist/api-test-scaffold-generator.jar generate --spec generation-spec.yaml --project /path/to/project
```

## 完整链路实践

```text
使用 $requirement-to-atc-workflow，将这份需求依次生成 TAD 和 YAML；前端用例生成 Web ATC，接口/服务端用例生成 Put 接口自动化脚手架；执行目标单例并完成诊断、修复、复跑和反馈沉淀。
```

Web ATC 依赖真实页面或已跑通黄金脚本。接口自动化依赖目标仓库中的黄金测试、API 封装和 RO/DTO 证据。缺少证据时 Skill 会标记待确认，不猜测页面路径、Java 类型、接口方法或业务枚举。执行闭环只有在根因有证据、目标单例复跑通过、反馈已回写或明确阻塞时才算完成。

## 上传 Git

在当前目录初始化并提交：

```bash
git init
git add .
git commit -m "feat: add UI and API test automation skills"
```

公开仓库前，请根据团队要求补充许可证，并检查示例中是否包含内部地址、真实账号、业务数据或截图链接。生成器示例只保留框架结构和占位数据，不复制目标业务项目源码。
