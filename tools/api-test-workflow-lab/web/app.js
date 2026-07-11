const state = {
  session: null,
  mode: new URLSearchParams(window.location.search).get("demo") === "1"
    ? "demo"
    : ["127.0.0.1", "localhost", "::1"].includes(window.location.hostname) ? "local" : "demo",
  gates: { inspect: null, generate: null, validate: null, compile: null, run: null },
};

const $ = (id) => document.getElementById(id);
const consoleOutput = $("consoleOutput");

async function api(path, options = {}) {
  if (state.mode === "demo") return demoApi(path, options);
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  const data = await response.json();
  if (!response.ok || data.error) throw new Error(data.error || `HTTP ${response.status}`);
  return data;
}

async function demoApi(path, options = {}) {
  const body = options.body ? JSON.parse(options.body) : {};
  const evidence = {
    path: "browser-demo/api-test-project", exists: true, pom: true, git: true,
    testClasses: 12, suiteFiles: 9, json5Files: 36, apiClasses: 5, dataPreparers: 4,
    samples: ["src/test/java/example/RecordQueryTests.java", "src/test/java/example/RecordCreateTests.java"],
  };
  if (path === "/api/health") return { status: "ok", jar: false, demo: true, version: "1.0.0" };
  if (path === "/api/example-spec") {
    const response = await fetch("./sample-spec.yaml");
    return { spec: await response.text() };
  }
  if (path.startsWith("/api/inspect")) return evidence;
  if (path === "/api/session") {
    return {
      id: "demo-session", sourceProject: body.project || "browser-demo/api-test-project",
      workspace: "browser-demo/workspace", mode: "copy", evidence,
      manifest: null, plan: null, planText: "", generatedFiles: [],
    };
  }
  if (path === "/api/generate") {
    const generatedFiles = [
      ".api-test-generator/api_record_create/executableization-plan.yaml",
      ".api-test-generator/api_record_create/scaffold-manifest.yaml",
      "src/test/java/com/example/automation/api_record_create/RecordCreateGeneratedTests.java",
      "src/test/resources/com/example/automation/api_record_create/01_有效管理员创建记录成功_req_staging.json5",
      "src/test/resources/com/example/automation/api_record_create/01_有效管理员创建记录成功_exp_staging.json5",
      "src/test/resources/com/example/automation/api_record_create/01_有效管理员创建记录成功_req_test.json5",
      "src/test/resources/com/example/automation/api_record_create/01_有效管理员创建记录成功_exp_test.json5",
      "src/test/resources/com/example/automation/api_record_create/记录管理-创建记录生成样例.yaml",
    ];
    const planText = "schemaVersion: 1\napiCode: api_record_create\nstatus: pending\nnextSkill: make-api-tests-executable\nfields:\n  - field: parent_id\n    kind: runtime-business-data\n    decision: pending\n  - field: role_code\n    kind: business-enum\n    decision: pending\n";
    return {
      command: "java -jar api-test-scaffold-generator.jar generate --spec generation-spec.yaml --project workspace",
      exitCode: 0, success: true, output: "演练生成完成\nFiles: 8",
      session: { id: "demo-session", sourceProject: "browser-demo/api-test-project", workspace: "browser-demo/workspace", mode: "copy", evidence: {...evidence, testClasses: 13, suiteFiles: 10, json5Files: 40}, manifest: "browser-demo/workspace/.api-test-generator/api_record_create/scaffold-manifest.yaml", plan: "browser-demo/workspace/.api-test-generator/api_record_create/executableization-plan.yaml", planText, generatedFiles },
    };
  }
  if (path === "/api/validate") return { command: "validate --strict-checksums", exitCode: 0, success: true, output: "Validation: PASS\nMissing: 0\nModified: 0\nMalformed: 0" };
  if (path === "/api/compile") return { command: "mvn -DskipTests test-compile", exitCode: 0, success: true, output: "[INFO] BUILD SUCCESS\n[INFO] GitHub Pages 演练模式未执行本机 Maven" };
  if (path === "/api/run") throw new Error("在线演练模式不调用真实接口，请在本地模式运行单例");
  throw new Error("演练接口不存在");
}

function toast(message, error = false) {
  const element = $("toast");
  element.textContent = message;
  element.className = `toast show${error ? " error" : ""}`;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { element.className = "toast"; }, 2600);
}

function appendLog(title, result) {
  const time = new Date().toLocaleTimeString("zh-CN", { hour12: false });
  const command = result?.command ? `\n$ ${result.command}` : "";
  const output = result?.output ? `\n${result.output.trim()}` : "";
  const success = result?.success === true;
  consoleOutput.innerHTML += `\n<span class="${success ? "log-success" : ""}">[${time}] ${escapeHtml(title)}</span>${escapeHtml(command + output)}\n`;
  consoleOutput.scrollTop = consoleOutput.scrollHeight;
}

function appendError(title, error) {
  const time = new Date().toLocaleTimeString("zh-CN", { hour12: false });
  consoleOutput.innerHTML += `\n<span class="log-error">[${time}] ${escapeHtml(title)}：${escapeHtml(error.message)}</span>\n`;
  consoleOutput.scrollTop = consoleOutput.scrollHeight;
  toast(error.message, true);
}

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[char]);
}

function setBusy(button, busy, label) {
  if (!button.dataset.label) button.dataset.label = button.innerHTML;
  button.disabled = busy;
  button.innerHTML = busy ? `<span class="spinner"></span>${label}` : button.dataset.label;
  if (!busy && window.lucide) lucide.createIcons();
}

function updateGate(name, value, label) {
  state.gates[name] = value;
  const row = document.querySelector(`[data-gate="${name}"]`);
  if (!row) return;
  row.classList.remove("pass", "fail");
  if (value === true) row.classList.add("pass");
  if (value === false) row.classList.add("fail");
  row.querySelector("b").textContent = label || (value === true ? "通过" : value === false ? "失败" : "等待");
  row.querySelector("svg")?.setAttribute("data-lucide", value === true ? "circle-check" : value === false ? "circle-x" : "circle");
  if (window.lucide) lucide.createIcons();
}

function activateStage(name) {
  const order = ["inspect", "spec", "generate", "data", "compile", "feedback"];
  const activeIndex = order.indexOf(name);
  document.querySelectorAll(".stage").forEach((stage, index) => {
    stage.classList.toggle("active", stage.dataset.stage === name);
    stage.classList.toggle("done", index < activeIndex);
  });
}

function renderEvidence(evidence) {
  const values = [evidence.testClasses, evidence.suiteFiles, evidence.json5Files, evidence.dataPreparers];
  document.querySelectorAll("#metrics strong").forEach((element, index) => { element.textContent = values[index] ?? "--"; });
  const valid = evidence.exists && evidence.pom;
  const status = $("evidenceStatus");
  status.textContent = valid ? "证据就绪" : "证据不足";
  status.className = `status ${valid ? "success" : "fail"}`;
  updateGate("inspect", valid);
  if (valid) activateStage("spec");
  return valid;
}

function renderSession(session) {
  state.session = session;
  $("sessionBadge").textContent = `${session.mode === "copy" ? "临时副本" : "原目录"} · ${session.id}`;
  renderEvidence(session.evidence);
  $("planViewer").textContent = session.planText || "尚未生成可执行化计划";
  const files = session.generatedFiles || [];
  $("artifactCount").textContent = files.length;
  $("artifactList").innerHTML = files.length
    ? files.map((file) => `<div><i data-lucide="file-code-2"></i><span>${escapeHtml(file)}</span></div>`).join("")
    : '<div class="empty-state"><i data-lucide="folder-open"></i><span>暂无产物</span></div>';
  $("generateButton").disabled = false;
  $("validateButton").disabled = !session.manifest;
  $("compileButton").disabled = !session.manifest;
  $("runButton").disabled = !session.manifest || state.mode === "demo";
  if (window.lucide) lucide.createIcons();
}

async function loadExample() {
  try {
    const data = await api("/api/example-spec");
    $("specEditor").value = data.spec;
    toast("已载入生成规格样例");
  } catch (error) { appendError("载入样例失败", error); }
}

async function inspect() {
  const button = $("inspectButton");
  setBusy(button, true, "检查中");
  try {
    if (state.mode === "local" && !$("projectPath").value.trim()) throw new Error("请输入本地测试项目目录");
    const evidence = await api(`/api/inspect?project=${encodeURIComponent($("projectPath").value)}`);
    renderEvidence(evidence);
    appendLog("框架取证完成", { success: evidence.exists && evidence.pom, output: JSON.stringify(evidence, null, 2) });
    toast(evidence.pom ? "框架证据已识别" : "未识别到 Maven 项目", !evidence.pom);
  } catch (error) { appendError("框架取证失败", error); }
  finally { setBusy(button, false); }
}

async function createSession() {
  const button = $("sessionButton");
  setBusy(button, true, "准备中");
  try {
    if (state.mode === "local" && !$("projectPath").value.trim()) throw new Error("请输入本地测试项目目录");
    const session = await api("/api/session", {
      method: "POST",
      body: JSON.stringify({ project: $("projectPath").value, mode: $("safeCopy").checked ? "copy" : "in-place" }),
    });
    renderSession(session);
    appendLog("实践会话已创建", { success: true, output: `工作目录：${session.workspace}` });
    toast("实践会话已创建");
  } catch (error) { appendError("创建会话失败", error); }
  finally { setBusy(button, false); }
}

async function generate() {
  const button = $("generateButton");
  setBusy(button, true, "生成中");
  try {
    const result = await api("/api/generate", {
      method: "POST",
      body: JSON.stringify({ sessionId: state.session.id, spec: $("specEditor").value }),
    });
    appendLog("脚手架生成", result);
    updateGate("generate", result.success);
    if (result.session) renderSession(result.session);
    if (result.success) { activateStage("generate"); toast("脚手架生成成功"); }
    else toast("生成失败，请查看日志", true);
  } catch (error) { updateGate("generate", false); appendError("脚手架生成失败", error); }
  finally { setBusy(button, false); }
}

async function validate() {
  const button = $("validateButton");
  setBusy(button, true, "校验中");
  try {
    const result = await api("/api/validate", { method: "POST", body: JSON.stringify({ sessionId: state.session.id, strict: true }) });
    appendLog("严格 Manifest 校验", result);
    updateGate("validate", result.success);
    if (result.success) { activateStage("data"); toast("严格校验通过"); }
  } catch (error) { updateGate("validate", false); appendError("Manifest 校验失败", error); }
  finally { setBusy(button, false); }
}

async function compileTests() {
  const button = $("compileButton");
  setBusy(button, true, "编译中");
  try {
    activateStage("compile");
    const result = await api("/api/compile", { method: "POST", body: JSON.stringify({ sessionId: state.session.id }) });
    appendLog("Maven 测试编译", result);
    updateGate("compile", result.success);
    toast(result.success ? "测试编译通过" : "测试编译失败", !result.success);
  } catch (error) { updateGate("compile", false); appendError("测试编译失败", error); }
  finally { setBusy(button, false); }
}

async function runTest() {
  if (!$("confirmRun").checked) { toast("请先确认允许调用真实接口", true); return; }
  const button = $("runButton");
  setBusy(button, true, "运行中");
  try {
    const result = await api("/api/run", {
      method: "POST",
      body: JSON.stringify({ sessionId: state.session.id, className: $("testClass").value, confirmed: true }),
    });
    appendLog("目标单例运行", result);
    updateGate("run", result.success, result.success ? "通过" : "失败");
    activateStage("feedback");
    toast(result.success ? "目标单例通过" : "目标单例失败", !result.success);
  } catch (error) { updateGate("run", false); appendError("目标单例运行失败", error); }
  finally { setBusy(button, false); }
}

document.querySelectorAll(".segmented button").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll(".segmented button").forEach((item) => item.classList.toggle("selected", item === button));
    $("specEditor").classList.toggle("hidden", button.dataset.view !== "spec");
    $("planViewer").classList.toggle("hidden", button.dataset.view !== "plan");
  });
});

$("inspectButton").addEventListener("click", inspect);
$("sessionButton").addEventListener("click", createSession);
$("exampleButton").addEventListener("click", loadExample);
$("generateButton").addEventListener("click", generate);
$("validateButton").addEventListener("click", validate);
$("compileButton").addEventListener("click", compileTests);
$("runButton").addEventListener("click", runTest);
$("clearLog").addEventListener("click", () => { consoleOutput.innerHTML = '<span class="muted">日志已清空</span>'; });
$("resetButton").addEventListener("click", () => window.location.reload());

async function bootstrap() {
  if (window.lucide) lucide.createIcons();
  try {
    const health = await api("/api/health");
    $("connectionStatus").classList.add("online");
    $("connectionStatus").lastChild.textContent = state.mode === "demo"
      ? " GitHub Pages · 演练模式"
      : health.jar ? " 本地服务 · JAR 就绪" : " 本地服务 · JAR 缺失";
    if (state.mode === "demo") {
      $("projectPath").value = "browser-demo/api-test-project";
      $("projectPath").disabled = true;
      $("safeCopy").disabled = true;
      $("confirmRun").disabled = true;
      $("runButton").title = "在线演练模式不调用真实接口";
    }
  } catch (error) { appendError("服务连接失败", error); }
  await loadExample();
}

bootstrap();
