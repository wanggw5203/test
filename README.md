# 需求到 Web ATC 中文 Skills

这是一组从知识库实践中提炼的通用测试生成 Skills，覆盖“需求结构化分析 -> YAML 测试用例 -> Web UI ATC 自动化”完整链路。Skill 的机器标识必须使用英文小写和连字符，面向使用者的说明、规则和默认提示词均已中文化。

## 目录

```text
skills/
├── requirement-to-tad/          # 原始需求 -> TAD/TRD/STD
├── tad-to-test-yaml/            # TAD/TRD/STD -> YAML 测试用例
├── test-yaml-to-web-atc/        # 功能 YAML -> Web ATC
└── requirement-to-atc-workflow/ # 端到端流程编排
```

每个子目录都是可独立安装的 Skill。前三个适合分阶段实践，第四个用于完整链路编排。

## 安装

将需要的 Skill 目录放入 Codex Skills 目录：

```bash
mkdir -p ~/.codex/skills
cp -R skills/requirement-to-tad ~/.codex/skills/
cp -R skills/tad-to-test-yaml ~/.codex/skills/
cp -R skills/test-yaml-to-web-atc ~/.codex/skills/
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

## 完整链路实践

```text
使用 $requirement-to-atc-workflow，将这份需求依次生成 TAD 分析、YAML 测试用例和 Web ATC；每个阶段校验通过后再继续。
```

Web ATC 依赖真实页面或已跑通黄金脚本。缺少这些证据时，Skill 会生成待校准草稿，不会猜测页面路径、字段、提示语和上传资源。

## 上传 Git

在当前目录初始化并提交：

```bash
git init
git add .
git commit -m "feat: add requirement-to-atc Chinese skills"
```

公开仓库前，请根据团队要求补充许可证，并检查知识库示例中是否包含内部地址、账号、业务数据或截图链接。本仓库当前只包含抽象规则，不复制业务项目中的敏感材料。
