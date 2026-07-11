# 接口自动化实践台

本地页面化实践“框架取证 -> 生成规格 -> 脚手架生成 -> Manifest 校验 -> 数据加工 -> 编译运行 -> 反馈闭环”。服务只监听 `127.0.0.1`，并且只提供固定的生成、校验、编译和单例运行接口。

## 启动

```bash
python3 tools/api-test-workflow-lab/server.py
```

默认地址：`http://127.0.0.1:8765`

不自动打开浏览器：

```bash
python3 tools/api-test-workflow-lab/server.py --no-open
```

本地模式连接真实生成器、项目文件和 Maven。若 `8765` 已被占用：

```bash
python3 tools/api-test-workflow-lab/server.py --port 8766
```

## GitHub Pages

仓库包含 `.github/workflows/pages.yml`。推送到 `main` 后，GitHub Actions 会发布：

```text
https://<github-user>.github.io/<repository>/
```

仓库首次启用时，在 GitHub 的 `Settings -> Pages -> Build and deployment -> Source` 选择 `GitHub Actions`，再手动运行 `Deploy workflow lab to GitHub Pages` 或推送一次页面文件。

Pages 版运行在浏览器演练模式，可完整操作取证、生成、校验和编译门禁，但不会读取访问者电脑上的项目，也不会执行 Java、Maven 或真实接口。需要真实执行时启动本地服务。

## 操作边界

- 默认勾选“临时副本”，服务通过本地 Git 克隆创建隔离工作目录。
- 关闭“临时副本”后会直接在目标项目生成文件，必须自行确认工作树状态。
- 页面不接受任意 Shell 命令。
- 真实单例运行需要显式勾选确认，并仅接受合法 Java 测试类名。
- 临时会话保存在系统临时目录，服务重启后会话索引失效。

## 检查

```bash
python3 -m unittest discover -s tools/api-test-workflow-lab/tests -v
```
