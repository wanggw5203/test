#!/usr/bin/env python3
"""Local-only practice UI for the API test scaffold workflow."""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import re
import shutil
import subprocess
import tempfile
import threading
import uuid
import webbrowser
from dataclasses import dataclass, field
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


APP_DIR = Path(__file__).resolve().parent
REPO_ROOT = APP_DIR.parents[1]
WEB_DIR = APP_DIR / "web"
JAR_PATH = REPO_ROOT / "dist" / "api-test-scaffold-generator.jar"
EXAMPLE_SPEC = REPO_ROOT / "tools" / "api-test-scaffold-generator" / "src" / "main" / "resources" / "example-spec.yaml"
CLASS_NAME = re.compile(r"^[A-Za-z_$][A-Za-z0-9_.$]*$")
LLM_STAGES = {
    "requirement-to-tad": REPO_ROOT / "skills" / "requirement-to-tad" / "SKILL.md",
    "tad-to-test-yaml": REPO_ROOT / "skills" / "tad-to-test-yaml" / "SKILL.md",
    "test-yaml-to-api-spec": REPO_ROOT / "skills" / "api-test-automation-workflow" / "SKILL.md",
    "api-feedback": REPO_ROOT / "skills" / "api-test-automation-workflow" / "references" / "api-feedback-contract.md",
}


@dataclass
class PracticeSession:
    id: str
    source_project: Path
    workspace: Path
    mode: str
    session_root: Path
    manifest: Path | None = None
    plan: Path | None = None
    generated_files: list[str] = field(default_factory=list)


SESSIONS: dict[str, PracticeSession] = {}
SESSIONS_LOCK = threading.Lock()


def run_command(args: list[str], cwd: Path, timeout: int = 240) -> dict[str, Any]:
    process = subprocess.run(
        args,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=timeout,
        check=False,
    )
    return {
        "command": " ".join(args),
        "exitCode": process.returncode,
        "output": process.stdout,
        "success": process.returncode == 0,
    }


def llm_status() -> dict[str, Any]:
    return {
        "configured": bool(os.environ.get("LLM_API_KEY")),
        "baseUrl": os.environ.get("LLM_BASE_URL", "https://api.openai.com/v1"),
        "model": os.environ.get("LLM_MODEL", "gpt-5-mini"),
        "protocol": "openai-compatible-chat-completions",
    }


def skill_context(stage: str) -> str:
    source = LLM_STAGES.get(stage)
    if source is None or not source.is_file():
        raise ValueError("不支持的大模型阶段")
    context = source.read_text(encoding="utf-8")
    if stage == "test-yaml-to-api-spec":
        reference = REPO_ROOT / "skills" / "api-test-automation-workflow" / "references" / "generator-spec.md"
        context += "\n\n" + reference.read_text(encoding="utf-8")
    return context


def stage_instruction(stage: str) -> str:
    instructions = {
        "requirement-to-tad": "输出结构化中文 TAD Markdown，包含背景、TRD、STD、规则编号、待确认项和追溯关系。只输出正文。",
        "tad-to-test-yaml": "输出可解析 YAML，根节点为 TestCases；包含 caseId、level、before、module、tags、thought、steps、state。优先覆盖接口和服务端场景。只输出 YAML。",
        "test-yaml-to-api-spec": "输出可供 api-test-scaffold-generator.jar 使用的 generation-spec YAML。首次生成就处理固定值、枚举、环境数据、唯一值和运行时业务数据：易失 ID 使用 __DATA_REF，写全 fieldHints 和 preparationLines；同时保留 executableization-plan 二次加工入口。Java 类型证据不足时使用 com.example 占位并在 thought 标记待校准。只输出 YAML。",
        "api-feedback": "根据执行证据输出 api.feedback.yaml，区分 passed、failed、blocked、skipped，包含 stage、rootCause、evidence、changes、upstreamFeedback 和 goldenSample。只输出 YAML。",
    }
    return instructions[stage]


def call_llm(stage: str, inputs: dict[str, Any]) -> dict[str, Any]:
    status = llm_status()
    api_key = os.environ.get("LLM_API_KEY")
    if not api_key:
        raise ValueError("尚未配置 LLM_API_KEY，请按 README 设置后重启服务")
    base_url = str(status["baseUrl"]).rstrip("/")
    endpoint = f"{base_url}/chat/completions"
    prompt = json.dumps(inputs, ensure_ascii=False, indent=2)
    payload = {
        "model": status["model"],
        "messages": [
            {"role": "system", "content": "你是测试工程工作流执行器。严格遵循下面的 Skill 规则。\n\n" + skill_context(stage)},
            {"role": "user", "content": stage_instruction(stage) + "\n\n输入：\n" + prompt},
        ],
    }
    request = Request(
        endpoint,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=180) as response:
            result = json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"大模型接口返回 HTTP {error.code}: {detail[:600]}") from error
    except URLError as error:
        raise RuntimeError(f"无法连接大模型接口：{error.reason}") from error
    try:
        content = result["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as error:
        raise RuntimeError("大模型响应缺少 choices[0].message.content") from error
    if isinstance(content, list):
        content = "\n".join(str(item.get("text", "")) if isinstance(item, dict) else str(item) for item in content)
    text = str(content).strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[-1].strip() == "```":
            text = "\n".join(lines[1:-1])
    return {"stage": stage, "model": status["model"], "content": text}


def inspect_project(project: Path) -> dict[str, Any]:
    pom = project / "pom.xml"
    tests = list((project / "src" / "test" / "java").rglob("*Tests.java")) if project.exists() else []
    suites = list((project / "src" / "test" / "resources").rglob("*.yaml")) if project.exists() else []
    json5 = list((project / "src" / "test" / "resources").rglob("*.json5")) if project.exists() else []
    api_classes = list((project / "src" / "main" / "java").rglob("*Api.java")) if project.exists() else []
    preparers = list((project / "src" / "test" / "java").rglob("*Preparer.java")) if project.exists() else []
    return {
        "path": str(project),
        "exists": project.is_dir(),
        "pom": pom.is_file(),
        "git": (project / ".git").exists(),
        "testClasses": len(tests),
        "suiteFiles": len(suites),
        "json5Files": len(json5),
        "apiClasses": len(api_classes),
        "dataPreparers": len(preparers),
        "samples": [str(path.relative_to(project)) for path in tests[:4]],
    }


def prepare_session(project: Path, mode: str) -> PracticeSession:
    if not project.is_dir() or not (project / "pom.xml").is_file():
        raise ValueError("目标目录不存在或缺少 pom.xml")
    session_id = uuid.uuid4().hex[:12]
    session_root = Path(tempfile.mkdtemp(prefix=f"api-workflow-{session_id}-"))
    if mode == "copy":
        workspace = session_root / "workspace"
        if (project / ".git").exists():
            result = run_command(["git", "clone", "--local", str(project), str(workspace)], session_root, 120)
            if not result["success"]:
                raise RuntimeError(result["output"])
        else:
            shutil.copytree(project, workspace, ignore=shutil.ignore_patterns("target", ".idea", ".DS_Store"))
    elif mode == "in-place":
        workspace = project
    else:
        raise ValueError("mode 只能是 copy 或 in-place")
    session = PracticeSession(session_id, project, workspace, mode, session_root)
    with SESSIONS_LOCK:
        SESSIONS[session_id] = session
    return session


def get_session(session_id: str) -> PracticeSession:
    with SESSIONS_LOCK:
        session = SESSIONS.get(session_id)
    if session is None:
        raise ValueError("实践会话不存在或服务已重启")
    return session


def parse_generation_output(session: PracticeSession, output: str) -> None:
    for line in output.splitlines():
        if line.startswith("Manifest: "):
            session.manifest = Path(line.removeprefix("Manifest: ").strip())
        elif line.startswith("Executableization plan: "):
            session.plan = Path(line.removeprefix("Executableization plan: ").strip())
    session.generated_files = [
        str(path.relative_to(session.workspace))
        for root in (session.workspace / "src" / "test", session.workspace / ".api-test-generator")
        if root.exists()
        for path in root.rglob("*")
        if path.is_file() and ("_generated" in str(path) or ".api-test-generator" in str(path))
    ]


class LabHandler(BaseHTTPRequestHandler):
    server_version = "ApiWorkflowLab/1.0"

    def log_message(self, format: str, *args: Any) -> None:
        print(f"[lab] {self.address_string()} {format % args}")

    def send_json(self, payload: Any, status: HTTPStatus = HTTPStatus.OK) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length > 2_000_000:
            raise ValueError("请求内容过大")
        raw = self.rfile.read(length)
        return json.loads(raw.decode("utf-8")) if raw else {}

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        try:
            if parsed.path == "/api/health":
                self.send_json({"status": "ok", "jar": JAR_PATH.is_file(), "version": "1.0.0"})
                return
            if parsed.path == "/api/llm/status":
                self.send_json(llm_status())
                return
            if parsed.path == "/api/example-spec":
                self.send_json({"spec": EXAMPLE_SPEC.read_text(encoding="utf-8")})
                return
            if parsed.path == "/api/inspect":
                project = Path(parse_qs(parsed.query).get("project", [""])[0]).expanduser().resolve()
                self.send_json(inspect_project(project))
                return
            if parsed.path == "/api/session":
                session = get_session(parse_qs(parsed.query).get("id", [""])[0])
                self.send_json(self.session_payload(session))
                return
            self.serve_static(parsed.path)
        except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as error:
            self.send_json({"error": str(error)}, HTTPStatus.BAD_REQUEST)

    def do_POST(self) -> None:
        try:
            body = self.read_json()
            if self.path == "/api/session":
                project = Path(str(body.get("project", ""))).expanduser().resolve()
                session = prepare_session(project, str(body.get("mode", "copy")))
                self.send_json(self.session_payload(session), HTTPStatus.CREATED)
                return
            if self.path == "/api/llm/execute":
                stage = str(body.get("stage", ""))
                inputs = body.get("inputs", {})
                if not isinstance(inputs, dict):
                    raise ValueError("inputs 必须是对象")
                self.send_json(call_llm(stage, inputs))
                return
            if self.path == "/api/generate":
                self.handle_generate(body)
                return
            if self.path == "/api/validate":
                self.handle_validate(body)
                return
            if self.path == "/api/compile":
                session = get_session(str(body.get("sessionId", "")))
                self.send_json(run_command(["mvn", "-DskipTests", "test-compile"], session.workspace, 300))
                return
            if self.path == "/api/run":
                self.handle_run(body)
                return
            self.send_json({"error": "接口不存在"}, HTTPStatus.NOT_FOUND)
        except subprocess.TimeoutExpired as error:
            self.send_json({"error": f"命令执行超时：{error.cmd}"}, HTTPStatus.REQUEST_TIMEOUT)
        except (ValueError, RuntimeError, OSError, json.JSONDecodeError) as error:
            self.send_json({"error": str(error)}, HTTPStatus.BAD_REQUEST)

    def handle_generate(self, body: dict[str, Any]) -> None:
        if not JAR_PATH.is_file():
            raise RuntimeError(f"生成器 JAR 不存在：{JAR_PATH}")
        session = get_session(str(body.get("sessionId", "")))
        spec = str(body.get("spec", "")).strip()
        if not spec:
            raise ValueError("生成规格不能为空")
        spec_path = session.session_root / "generation-spec.yaml"
        spec_path.write_text(spec + "\n", encoding="utf-8")
        result = run_command(
            ["java", "-jar", str(JAR_PATH), "generate", "--spec", str(spec_path), "--project", str(session.workspace)],
            session.workspace,
            180,
        )
        if result["success"]:
            parse_generation_output(session, result["output"])
        result["session"] = self.session_payload(session)
        self.send_json(result)

    def handle_validate(self, body: dict[str, Any]) -> None:
        session = get_session(str(body.get("sessionId", "")))
        if session.manifest is None or not session.manifest.is_file():
            raise ValueError("请先生成脚手架")
        args = [
            "java", "-jar", str(JAR_PATH), "validate",
            "--project", str(session.workspace), "--manifest", str(session.manifest),
        ]
        if bool(body.get("strict", False)):
            args.append("--strict-checksums")
        self.send_json(run_command(args, session.workspace, 120))

    def handle_run(self, body: dict[str, Any]) -> None:
        session = get_session(str(body.get("sessionId", "")))
        class_name = str(body.get("className", "")).strip()
        if not CLASS_NAME.fullmatch(class_name):
            raise ValueError("请输入合法的测试类名")
        if body.get("confirmed") is not True:
            raise ValueError("运行真实单例前必须确认可能产生测试数据")
        self.send_json(run_command(["mvn", f"-Dtest={class_name}", "test"], session.workspace, 420))

    def session_payload(self, session: PracticeSession) -> dict[str, Any]:
        return {
            "id": session.id,
            "sourceProject": str(session.source_project),
            "workspace": str(session.workspace),
            "mode": session.mode,
            "evidence": inspect_project(session.workspace),
            "manifest": str(session.manifest) if session.manifest else None,
            "plan": str(session.plan) if session.plan else None,
            "planText": session.plan.read_text(encoding="utf-8") if session.plan and session.plan.is_file() else "",
            "generatedFiles": sorted(session.generated_files),
        }

    def serve_static(self, request_path: str) -> None:
        relative = "index.html" if request_path in ("", "/") else request_path.lstrip("/")
        target = (WEB_DIR / relative).resolve()
        if WEB_DIR.resolve() not in target.parents and target != WEB_DIR.resolve():
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        if not target.is_file():
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        data = target.read_bytes()
        content_type = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", f"{content_type}; charset=utf-8" if content_type.startswith("text/") else content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main() -> None:
    parser = argparse.ArgumentParser(description="Start the local API automation workflow lab")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--no-open", action="store_true")
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), LabHandler)
    url = f"http://{args.host}:{server.server_port}"
    print(f"API 自动化实践台已启动：{url}")
    if not args.no_open:
        threading.Timer(0.5, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
