# 需求到 Web ATC 中文 Skills

这是一组从知识库实践中提炼的通用测试 Skills，覆盖“需求结构化分析 -> YAML 测试用例 -> Web UI ATC 自动化 -> 执行诊断与反馈沉淀”完整闭环。Skill 的机器标识必须使用英文小写和连字符，面向使用者的说明、规则和默认提示词均已中文化。

## 目录

```text
skills/
├── requirement-to-tad/          # 原始需求 -> TAD/TRD/STD
├── tad-to-test-yaml/            # TAD/TRD/STD -> YAML 测试用例
├── test-yaml-to-web-atc/        # 功能 YAML -> Web ATC
├── close-atc-feedback-loop/      # ATC 执行 -> 诊断、修复、复跑与反馈
└── requirement-to-atc-workflow/ # 四阶段端到端闭环编排
```

每个子目录都是可独立安装的 Skill。前四个适合分阶段实践，第五个用于完整闭环编排。

## 安装

将需要的 Skill 目录放入 Codex Skills 目录：

```bash
mkdir -p ~/.codex/skills
cp -R skills/requirement-to-tad ~/.codex/skills/
cp -R skills/tad-to-test-yaml ~/.codex/skills/
cp -R skills/test-yaml-to-web-atc ~/.codex/skills/
cp -R skills/close-atc-feedback-loop ~/.codex/skills/
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

## 完整链路实践

```text
使用 $requirement-to-atc-workflow，将这份需求依次生成 TAD、YAML 和 Web ATC，执行目标用例，并根据结果完成诊断、修复、复跑和反馈沉淀。
```

Web ATC 依赖真实页面或已跑通黄金脚本。缺少这些证据时，Skill 会生成待校准草稿，不会猜测页面路径、字段、提示语和上传资源。执行闭环只有在根因有证据、目标单例复跑通过、反馈已回写或明确阻塞时才算完成。

## 上传 Git

在当前目录初始化并提交：

```bash
git init
git add .
git commit -m "feat: add requirement-to-atc Chinese skills"
```

公开仓库前，请根据团队要求补充许可证，并检查知识库示例中是否包含内部地址、账号、业务数据或截图链接。本仓库当前只包含抽象规则，不复制业务项目中的敏感材料。
