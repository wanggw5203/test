package io.github.aitestflow.ui;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import io.github.aitestflow.discovery.EndpointProtocol;
import io.github.aitestflow.discovery.EndpointSelectorKind;
import io.github.aitestflow.discovery.DiscoveredEndpoint;
import io.github.aitestflow.enterprise.AssetPlan;
import io.github.aitestflow.enterprise.InputAssetService;
import io.github.aitestflow.enterprise.QualityGateService;
import io.github.aitestflow.enterprise.TraceabilityService;
import io.github.aitestflow.enterprise.WorkspaceConfig;
import io.github.aitestflow.enterprise.WorkspaceManager;
import io.github.aitestflow.model.PipelineStage;
import io.github.aitestflow.service.ArtifactStore;
import io.github.aitestflow.service.LlmClient;
import io.github.aitestflow.service.GoldenDemo;
import io.github.aitestflow.service.InterfaceEvidenceService;
import io.github.aitestflow.service.RequirementImporter;
import io.github.aitestflow.service.ScaffoldGenerationService;
import io.github.aitestflow.service.SkillRepository;
import io.github.aitestflow.initializer.ProjectInitializer;
import io.github.aitestflow.settings.AiTestFlowSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.Box;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class AiTestFlowPanel extends JPanel {
    private static final Logger LOG = Logger.getInstance(AiTestFlowPanel.class);
    private final Project project;
    private final SkillRepository skills;
    private final LlmClient llm;
    private final ArtifactStore store;
    private final WorkspaceManager workspace;
    private final InputAssetService inputAssets;
    private final TraceabilityService traceability;
    private final QualityGateService qualityGates = new QualityGateService();
    private final RequirementImporter importer = new RequirementImporter();
    private final InterfaceEvidenceService interfaceEvidence = new InterfaceEvidenceService();
    private final ScaffoldGenerationService scaffoldGenerator;
    private final Map<PipelineStage, JBTextArea> editors = new EnumMap<>(PipelineStage.class);
    private final JBTextArea requirement = editor("在这里输入 PRD，或选择项目中的 MD、Word、PDF、HTML、原型截图……");
    private final JBTextArea evidence = editor("");
    private final JBTextArea log = editor("");
    private final JTabbedPane tabs = new JTabbedPane();
    private final JBLabel sourceLabel = new JBLabel("手工输入");
    private final JBTextField tadPath = new JBTextField();
    private final JBTextField testcasePath = new JBTextField();
    private final JBTextField apiSpecPath = new JBTextField();
    private final JComboBox<EndpointProtocol> interfaceProtocol =
            new JComboBox<>(new EndpointProtocol[]{EndpointProtocol.HTTP, EndpointProtocol.DUBBO});
    private final JComboBox<EndpointSelectorKind> selectorKind = new JComboBox<>();
    private final JBTextField selectorValue = new JBTextField();
    private List<RequirementImporter.ImageAttachment> imageAttachments = List.of();
    private List<Path> currentSources = List.of();
    private Path currentSource;
    private AssetPlan currentPlan;
    private volatile boolean running;
    private EnterpriseWorkbench workbench;
    private Path lastScaffoldManifest;
    private final List<JComponent> configurationControls = new ArrayList<>();

    private record ExecutionSnapshot(String evidence, String evidencePath,
                                     List<RequirementImporter.ImageAttachment> images, List<String> inputPaths,
                                     AssetPlan plan, WorkspaceConfig config, EndpointProtocol protocol,
                                     EndpointSelectorKind selectorKind, String selectorValue,
                                     List<DiscoveredEndpoint> discoveredEndpoints) {}

    public AiTestFlowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.skills = new SkillRepository(project);
        this.llm = new LlmClient(project);
        this.store = new ArtifactStore(project);
        this.scaffoldGenerator = new ScaffoldGenerationService(project);
        this.workspace = new WorkspaceManager(project, store);
        this.inputAssets = new InputAssetService(project, store);
        this.traceability = new TraceabilityService(store);
        this.currentSource = projectRoot().resolve("docs/requirements/manual-prd.md");
        this.currentSources = List.of(currentSource);
        setBorder(JBUI.Borders.empty());

        tabs.addTab("需求", scroll(requirement));
        addEditor(PipelineStage.REQUIREMENT_TO_TAD, "TAD / STD");
        addEditor(PipelineStage.TAD_TO_TEST_CASES, "测试用例");
        addEditor(PipelineStage.TEST_CASES_TO_API_SPEC, "接口规格");
        addEditor(PipelineStage.TEST_CASES_TO_WEB_ATC, "Web ATC");
        addEditor(PipelineStage.FEEDBACK, "反馈闭环");
        tabs.addTab("证据", scroll(evidence));
        log.setEditable(false);
        tabs.addTab("运行日志", scroll(log));

        alignSelectorWithProtocol();
        selectorValue.getEmptyText().setText("HTTP: GET /api/orders；Dubbo: com.demo.OrderService#query(java.util.Map)");
        interfaceProtocol.addActionListener(event -> alignSelectorWithProtocol());
        workbench = new EnterpriseWorkbench(project.getName(), tabs, configurationPanel(),
                () -> guarded(this::chooseRequirementFiles), () -> guarded(this::loadGoldenDemo), () -> guarded(this::runPipeline),
                () -> guarded(this::showModelSettings), () -> guarded(this::openOutputDirectory));
        workbench.addStep("需求分析", "PRD → TAD / STD", 1, () -> guarded(() -> runStage(PipelineStage.REQUIREMENT_TO_TAD)));
        workbench.addStep("测试用例", "业务规则 → YAML 用例", 2, () -> guarded(() -> runStage(PipelineStage.TAD_TO_TEST_CASES)));
        workbench.addStep("接口规格", "HTTP / Dubbo 契约", 3, () -> guarded(() -> runStage(PipelineStage.TEST_CASES_TO_API_SPEC)));
        workbench.addStep("接口自动化", "TestNG · YAML · JSON5", 3, () -> guarded(this::runScaffoldGeneration), () -> guarded(this::openScaffoldManifest));
        workbench.addStep("Web 自动化", "页面证据 → Web ATC", 4, () -> guarded(() -> runStage(PipelineStage.TEST_CASES_TO_WEB_ATC)));
        workbench.addStep("反馈与追溯", "执行证据 → 反馈报告", 5, () -> guarded(() -> runStage(PipelineStage.FEEDBACK)));
        add(workbench, BorderLayout.CENTER);
        selectorKind.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                String display = value instanceof EndpointSelectorKind kind ? switch (kind) {
                    case HTTP_PATH -> "HTTP 方法与路径";
                    case DUBBO_METHOD -> "Dubbo 方法签名";
                    case JAVA_SYMBOL -> "Java 类与方法";
                    case API_CODE -> "接口编码";
                } : "";
                return super.getListCellRendererComponent(list, display, index, selected, focus);
            }
        });
        configureTargets(currentSource);
        if (AiTestFlowSettings.getInstance(project).data().demoMode) loadGoldenDemo();
        refreshMode();
    }

    private JComponent configurationPanel() {
        JPanel content = WorkbenchStyle.column();
        content.setBorder(JBUI.Borders.empty(12));
        content.add(button("创建自动化项目（HTTP / Dubbo / SDK）", this::createAutomationProject));
        content.add(Box.createVerticalStrut(12));
        JPanel inputs = WorkbenchStyle.column();
        sourceLabel.setToolTipText("来源文件会登记到当前项目，并进入运行追溯记录");
        sourceLabel.setAlignmentX(Component.LEFT_ALIGNMENT); inputs.add(sourceLabel);
        inputs.add(Box.createVerticalStrut(12));
        inputs.add(button("载入当前编辑文件", this::loadCurrentFile));
        content.add(WorkbenchStyle.card("输入材料", "支持 MD / Word / PDF / 原型", inputs));
        content.add(Box.createVerticalStrut(12));
        JPanel endpoint = WorkbenchStyle.column();
        WorkbenchStyle.field(endpoint, "接口协议", interfaceProtocol);
        WorkbenchStyle.field(endpoint, "定位方式", selectorKind);
        WorkbenchStyle.field(endpoint, "接口目标", selectorValue);
        content.add(WorkbenchStyle.card("接口目标", "生成规格与脚手架的输入", endpoint));
        content.add(Box.createVerticalStrut(12));
        JPanel paths = WorkbenchStyle.column();
        WorkbenchStyle.field(paths, "TAD / STD", tadPath);
        WorkbenchStyle.field(paths, "测试用例 YAML", testcasePath);
        WorkbenchStyle.field(paths, "接口 generation-spec", apiSpecPath);
        content.add(WorkbenchStyle.card("项目输出", "项目相对路径 · Git 版本管理", paths));
        content.add(Box.createVerticalStrut(12));
        content.add(button("初始化项目工作区", this::initializeWorkspace));
        configurationControls.addAll(List.of(interfaceProtocol, selectorKind, selectorValue,
                tadPath, testcasePath, apiSpecPath));
        return content;
    }

    private void showModelSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "AI Test Flow");
        refreshMode();
    }

    private void createAutomationProject() {
        ProjectInitializationDialog dialog = new ProjectInitializationDialog(project, projectRoot().resolve("new-automation-project"));
        if (!dialog.showAndGet()) return;
        Path target = dialog.target();
        var options = dialog.options();
        setRunning(true);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "创建 " + options.type() + " 自动化项目", false) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                try {
                    var created = new ProjectInitializer().initialize(target, options);
                    appendLog("已创建 " + options.type() + " 自动化项目：" + target + "；" + created.size() + " 个文件。请在 IDEA 中打开目标目录并导入 Maven。\n");
                    UiDispatch.run(() -> {
                        if (project.isDisposed()) return;
                        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target);
                        Messages.showInfoMessage(project, "项目已生成：\n" + target
                                + "\n\n在 IDEA 中打开该目录并导入 Maven，然后运行 mvn test 验证初始化。\n框架依赖需已发布到可访问的 Maven 仓库。", "创建自动化项目完成");
                    });
                } catch (Exception error) {
                    appendLog("项目初始化失败：" + error.getMessage() + "\n");
                    UiDispatch.run(() -> { if (!project.isDisposed()) Messages.showErrorDialog(project, String.valueOf(error.getMessage()), "项目初始化失败"); });
                } finally { setRunning(false); }
            }
        });
    }
    private void refreshMode() {
        var settings = AiTestFlowSettings.getInstance(project).data();
        workbench.setMode(settings.demoMode, settings.model);
    }
    private void setRunning(boolean value) {
        running = value;
        Runnable update = () -> {
            workbench.setBusy(value);
            configurationControls.forEach(control -> control.setEnabled(!value));
        };
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else ApplicationManager.getApplication().invokeLater(update);
    }
    private static int stepIndex(PipelineStage stage) {
        return switch (stage) {
            case REQUIREMENT_TO_TAD -> 0;
            case TAD_TO_TEST_CASES -> 1;
            case TEST_CASES_TO_API_SPEC -> 2;
            case TEST_CASES_TO_WEB_ATC -> 4;
            case FEEDBACK -> 5;
        };
    }

    private void loadGoldenDemo() {
        if (running) { info("已有生成任务正在运行。"); return; }
        var settings = AiTestFlowSettings.getInstance(project).data();
        settings.demoMode = true;
        settings.goldenDataset = GoldenDemo.ID;
        boolean dubbo = selectedProtocol() == EndpointProtocol.DUBBO;
        selectorKind.setSelectedItem(dubbo ? EndpointSelectorKind.DUBBO_METHOD : EndpointSelectorKind.HTTP_PATH);
        selectorValue.setText(dubbo ? GoldenDemo.DUBBO_SELECTOR : GoldenDemo.HTTP_SELECTOR);
        requirement.setText(GoldenDemo.requirement());
        evidence.setText(GoldenDemo.evidence(dubbo));
        imageAttachments = List.of();
        currentSources = List.of();
        configureTargets(projectRoot().resolve("quality-assets/inputs/manual/golden-order-create-v1.prd.md"));
        sourceLabel.setText("黄金集 " + GoldenDemo.ID + "（生成时登记到当前项目）");
        for (JBTextArea area : editors.values()) area.setText("");
        lastScaffoldManifest = null;
        tabs.setSelectedIndex(0);
        workbench.resetProgress(); refreshMode();
        appendLog("已启用黄金集 " + GoldenDemo.ID + "：所有模型阶段返回内置固定响应，无需 API Key。请选择运行完整链路。\n");
    }
    private void addEditor(PipelineStage stage, String title) { JBTextArea area = editor(""); editors.put(stage, area); tabs.addTab(title, scroll(area)); }
    private JButton button(String label, Runnable action) {
        JButton button = WorkbenchStyle.action(label, () -> guarded(action));
        configurationControls.add(button);
        return button;
    }
    private void guarded(Runnable action) {
        try { action.run(); }
        catch (Exception exception) { fail(PipelineStage.REQUIREMENT_TO_TAD, exception); }
    }
    private static JBTextArea editor(String value) { JBTextArea area = new JBTextArea(value); area.setLineWrap(true); area.setWrapStyleWord(true); area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13)); area.setBorder(JBUI.Borders.empty(8)); return area; }
    private static JBScrollPane scroll(JBTextArea area) { return new JBScrollPane(area); }

    private void chooseRequirementFiles() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, true)
                .withTitle("选择 PRD、Word、PDF、HTML 或原型截图").withDescription("可多选并合并为一个需求；产物始终写入当前项目");
        VirtualFile[] selected = FileChooser.chooseFiles(descriptor, project, projectRootFile());
        if (selected.length > 0) importFiles(Arrays.asList(selected));
    }
    private void chooseBatchFiles() {
        if (running) { info("已有生成任务正在运行。"); return; }
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, true)
                .withTitle("批量选择需求材料").withDescription("每个文件独立生成全部资产和审计记录");
        VirtualFile[] selected = FileChooser.chooseFiles(descriptor, project, projectRootFile());
        if (selected.length > 0) runBatch(Arrays.stream(selected).map(f -> VfsUtilCore.virtualToIoFile(f).toPath()).toList());
    }
    private VirtualFile projectRootFile() { return LocalFileSystem.getInstance().findFileByNioFile(projectRoot()); }

    private void initializeWorkspace() {
        if (running) return;
        try {
            Path config = workspace.initialize(); configureTargets(currentSource);
            appendLog("企业工作区已初始化：" + config + "\n");
            info("企业工作区已初始化。共享规范、资产目录和追溯目录均已写入项目，可提交 Git。");
        } catch (Exception exception) { fail(PipelineStage.REQUIREMENT_TO_TAD, exception); }
    }

    private void loadCurrentFile() {
        var textEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (textEditor == null) { info("当前没有打开的文本文件，可使用“选择需求材料”。"); return; }
        VirtualFile file = FileDocumentManager.getInstance().getFile(textEditor.getDocument());
        String selected = textEditor.getSelectionModel().getSelectedText();
        if (selected != null && !selected.isBlank()) {
            requirement.setText(selected); imageAttachments = List.of();
            currentSources = List.of();
            currentSource = projectRoot().resolve("quality-assets/inputs/manual/editor-selection.prd.txt");
            configureTargets(currentSource);
            sourceLabel.setText(file == null ? "当前编辑器选区（生成前登记到项目）" : file.getPresentableUrl() + "（选区，生成前登记到项目）"); tabs.setSelectedIndex(0);
        } else if (file == null) { requirement.setText(textEditor.getDocument().getText()); sourceLabel.setText("当前编辑器"); }
        else importFiles(List.of(file));
    }

    private void importFiles(List<VirtualFile> files) {
        if (running) { info("已有任务正在运行。"); return; }
        List<Path> paths = files.stream().map(file -> VfsUtilCore.virtualToIoFile(file).toPath()).toList(); setRunning(true);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "读取需求材料", true) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                try {
                    workspace.initialize();
                    WorkspaceConfig config = workspace.load();
                    List<Path> managedPaths = inputAssets.registerFiles(paths, config);
                    RequirementImporter.ImportedMaterial material = importer.read(managedPaths);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        requirement.setText(material.text()); imageAttachments = material.images(); currentSources = material.sources(); configureTargets(material.sources().get(0));
                        sourceLabel.setText(material.sources().size() == 1 ? inputAssets.relative(material.sources().get(0)) : inputAssets.relative(material.sources().get(0)) + " 等 " + material.sources().size() + " 个项目输入");
                        tabs.setSelectedIndex(0);
                    });
                    appendLog("已将 " + material.sources().size() + " 个需求材料登记到项目，视觉附件 " + material.images().size() + " 个；现在可由 Git 追踪。\n");
                } catch (Exception exception) { fail(PipelineStage.REQUIREMENT_TO_TAD, exception); } finally { setRunning(false); }
            }
        });
    }

    private void configureTargets(Path source) {
        currentSource = source.toAbsolutePath().normalize();
        WorkspaceConfig config;
        try {
            config = workspace.load();
        } catch (IOException exception) {
            config = new WorkspaceConfig();
            appendLog("配置待修复：" + exception.getMessage() + "\n");
        }
        currentPlan = AssetPlan.forSource(projectRoot(), currentSource, config);
        tadPath.setText(currentPlan.tadPath()); testcasePath.setText(currentPlan.testcasePath()); apiSpecPath.setText(currentPlan.apiSpecPath());
    }

    private void runStage(PipelineStage stage) {
        if (running) { info("已有生成任务正在运行。"); return; }
        if (stage == PipelineStage.TEST_CASES_TO_API_SPEC && !hasInterfaceSelector()) {
            warnMissingInterfaceSelector(); return;
        }
        String input = inputFor(stage);
        if (input.isBlank()) { Messages.showWarningDialog(project, "上一阶段内容为空，请先生成或粘贴输入。", "AI Test Flow"); return; }
        String sourceContent = requirement.getText();
        ExecutionSnapshot requestedSnapshot = snapshotExecutionState(); setRunning(true);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, stage.displayName(), true) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                TraceabilityService.RunSession session = null;
                try {
                    ExecutionSnapshot snapshot = prepareSnapshot(requestedSnapshot, sourceContent);
                    session = beginSession(snapshot, sourceContent); execute(stage, input, indicator, snapshot, session); session.complete();
                }
                catch (Exception exception) { if (session != null) session.failed(stage, exception); fail(stage, exception); }
                finally { setRunning(false); }
            }
        });
    }

    private void runPipeline() {
        if (running) { info("已有生成任务正在运行。"); return; }
        if (!hasInterfaceSelector()) { warnMissingInterfaceSelector(); return; }
        String sourceContent = requirement.getText();
        if (sourceContent.isBlank()) { Messages.showWarningDialog(project, "请先输入或导入需求材料。", "AI Test Flow"); return; }
        ExecutionSnapshot requestedSnapshot = snapshotExecutionState(); workbench.resetProgress(); setRunning(true);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "运行全部资产链路", true) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                PipelineStage active = PipelineStage.REQUIREMENT_TO_TAD; TraceabilityService.RunSession session = null;
                try {
                    ExecutionSnapshot snapshot = prepareSnapshot(requestedSnapshot, sourceContent);
                    session = beginSession(snapshot, sourceContent);
                    String tad = execute(active, sourceContent, indicator, snapshot, session);
                    active = PipelineStage.TAD_TO_TEST_CASES; String cases = execute(active, tad, indicator, snapshot, session);
                    active = PipelineStage.TEST_CASES_TO_API_SPEC; execute(active, cases, indicator, snapshot, session);
                    generateScaffold(indicator, snapshot, session);
                    active = PipelineStage.TEST_CASES_TO_WEB_ATC; execute(active, cases, indicator, snapshot, session);
                    active = PipelineStage.FEEDBACK; execute(active, snapshot.evidence(), indicator, snapshot, session);
                    session.complete(); appendLog("完整链路完成：分析、用例、接口规格、TestNG 脚手架、Web ATC、反馈和追溯记录均已写入项目。\n");
                } catch (Exception exception) { if (session != null) session.failed(active, exception); fail(active, exception); }
                finally { setRunning(false); }
            }
        });
    }

    private String execute(PipelineStage stage, String input, ProgressIndicator indicator,
                           ExecutionSnapshot snapshot, TraceabilityService.RunSession session) throws Exception {
        indicator.checkCanceled(); indicator.setText(stage.displayName());
        ApplicationManager.getApplication().invokeLater(() -> workbench.stage(stepIndex(stage), "处理中…", stage.displayName()));
        appendLog("开始：" + stage.displayName() + "（Skill: " + stage.skillName() + "）\n");
        String outputPath = snapshot.plan().pathFor(stage); ArtifactStore.Revision expected = store.revision(outputPath);
        String skill = skills.load(stage.skillName());
        List<RequirementImporter.ImageAttachment> images = stage == PipelineStage.REQUIREMENT_TO_TAD ? snapshot.images() : List.of();
        String result = llm.execute(stage, skill, input, snapshot.evidence(), images); indicator.checkCanceled();
        QualityGateService.InterfaceTarget expectedInterface = stage == PipelineStage.TEST_CASES_TO_API_SPEC
                ? new QualityGateService.InterfaceTarget(snapshot.protocol().name(),
                snapshot.selectorKind().name(), snapshot.selectorValue(),
                QualityGateService.extractCaseIds(input), snapshot.discoveredEndpoints()) : null;
        QualityGateService.GateResult gate = qualityGates.validate(stage, result, snapshot.config(), expectedInterface);
        if (!gate.passed()) {
            String rejected = session.reject(stage, result, gate);
            throw new IOException("质量门禁未通过，正式资产未覆盖。候选产物: " + rejected + "；" + String.join("；", gate.errors()));
        }
        Path saved = store.save(outputPath, result, expected); session.passed(stage, outputPath, result, gate);
        ApplicationManager.getApplication().invokeLater(() -> {
            workbench.stage(stepIndex(stage), "已生成", outputPath);
            editors.get(stage).setText(result); tabs.setSelectedComponent(editors.get(stage).getParent().getParent());
            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(saved); if (file != null) FileEditorManager.getInstance(project).openFile(file, true);
        });
        appendLog("门禁通过并写入：" + saved + "\n"); return result;
    }

    private void runScaffoldGeneration() {
        if (running) { info("已有生成任务正在运行。"); return; }
        if (!hasInterfaceSelector()) { warnMissingInterfaceSelector(); return; }
        String sourceContent = requirement.getText();
        ExecutionSnapshot requestedSnapshot = snapshotExecutionState();
        setRunning(true);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "生成 TestNG 接口自动化脚手架", true) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                TraceabilityService.RunSession session = null;
                try {
                    ExecutionSnapshot snapshot = prepareSnapshot(requestedSnapshot, sourceContent);
                    session = beginSession(snapshot, sourceContent);
                    generateScaffold(indicator, snapshot, session);
                    session.complete();
                    appendLog("接口自动化生成完成，Java、YAML、JSON5 与清单已通过校验。\n");
                } catch (Exception exception) {
                    if (session != null) session.failed(PipelineStage.TEST_CASES_TO_API_SPEC, exception);
                    fail(PipelineStage.TEST_CASES_TO_API_SPEC, exception);
                } finally { setRunning(false); }
            }
        });
    }

    private ScaffoldGenerationService.Result generateScaffold(
            ProgressIndicator indicator, ExecutionSnapshot snapshot,
            TraceabilityService.RunSession session) throws IOException {
        indicator.checkCanceled();
        ApplicationManager.getApplication().invokeLater(() -> workbench.stage(3, "处理中…", "生成 TestNG 接口脚手架"));
        String specification = java.nio.file.Files.readString(store.resolve(snapshot.plan().apiSpecPath()));
        QualityGateService.GateResult gate = qualityGates.validate(PipelineStage.TEST_CASES_TO_API_SPEC,
                specification, snapshot.config(), new QualityGateService.InterfaceTarget(
                snapshot.protocol().name(), snapshot.selectorKind().name(), snapshot.selectorValue(),
                List.of(), snapshot.discoveredEndpoints()));
        if (!gate.passed()) throw new IOException("脚手架生成前校验失败：" + String.join("；", gate.errors()));
        indicator.setText("生成 " + snapshot.protocol() + " TestNG 接口脚手架");
        appendLog("开始：根据 " + snapshot.plan().apiSpecPath() + " 生成 "
                + snapshot.protocol() + " TestNG 脚手架和用例数据\n");
        ScaffoldGenerationService.Result result = scaffoldGenerator.generate(snapshot.plan().apiSpecPath(), snapshot.plan().testcasePath());
        session.scaffold(snapshot.protocol().name(), snapshot.selectorKind().name(), snapshot.selectorValue(),
                result.apiCode(), result.manifestPath(), result.executableizationPlanPath(),
                result.generatedFiles());
        appendLog("脚手架生成并校验通过：" + result.manifestPath() + "；共 "
                + result.generatedFiles().size() + " 个项目资产。\n");
        Path manifestPath = store.resolve(result.manifestPath());
        ApplicationManager.getApplication().invokeLater(() -> {
            lastScaffoldManifest = manifestPath;
            workbench.stage(3, "已生成", result.manifestPath());
            VirtualFile manifest = LocalFileSystem.getInstance()
                    .refreshAndFindFileByNioFile(manifestPath);
            if (manifest != null) FileEditorManager.getInstance(project).openFile(manifest, true);
        });
        return result;
    }

    private void runBatch(List<Path> sources) {
        if (sources.size() > 1) {
            Messages.showWarningDialog(project, "接口脚手架需要逐需求指定接口。请每次选择一个需求运行完整链路，以保留各接口的独立资产和追溯关系。", "AI Test Flow");
            return;
        }
        if (!hasInterfaceSelector()) { warnMissingInterfaceSelector(); return; }
        String batchEvidence = evidence.getText();
        InterfaceEvidenceService.Evidence batchDiscovery = collectInterfaceEvidence();
        String governedBatchEvidence = mergeEvidence(batchEvidence, batchDiscovery.json());
        setRunning(true);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "批量生成企业测试资产", true) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                try {
                    workspace.initialize();
                    WorkspaceConfig config = workspace.load();
                    if (sources.size() > config.maxBatchSize) throw new IOException("单批最多处理 " + config.maxBatchSize + " 个需求文件");
                    int completed = 0;
                    for (Path source : sources) {
                        indicator.checkCanceled(); indicator.setFraction((double) completed / sources.size());
                        Path managedSource = inputAssets.registerFile(source, config);
                        RequirementImporter.ImportedMaterial material = importer.read(List.of(managedSource));
                        AssetPlan plan = AssetPlan.forSource(projectRoot(), managedSource, config);
                        Path evidenceAsset = inputAssets.registerEvidence(plan.assetId(), governedBatchEvidence, config);
                        ExecutionSnapshot snapshot = new ExecutionSnapshot(governedBatchEvidence,
                                evidenceAsset == null ? "" : inputAssets.relative(evidenceAsset), material.images(),
                                List.of(inputAssets.relative(managedSource)), plan, config,
                                batchDiscovery.query().protocol(), batchDiscovery.query().selectorKind(), batchDiscovery.query().value(),
                                batchDiscovery.endpoints());
                        TraceabilityService.RunSession session = beginSession(snapshot, material.text()); PipelineStage active = PipelineStage.REQUIREMENT_TO_TAD;
                        try {
                            String tad = execute(active, material.text(), indicator, snapshot, session);
                            active = PipelineStage.TAD_TO_TEST_CASES; String cases = execute(active, tad, indicator, snapshot, session);
                            active = PipelineStage.TEST_CASES_TO_API_SPEC; execute(active, cases, indicator, snapshot, session);
                            generateScaffold(indicator, snapshot, session);
                            active = PipelineStage.TEST_CASES_TO_WEB_ATC; execute(active, cases, indicator, snapshot, session);
                            active = PipelineStage.FEEDBACK; execute(active, batchEvidence, indicator, snapshot, session);
                            session.complete(); completed++; appendLog("批量完成 [" + completed + "/" + sources.size() + "]: " + source + "\n");
                        } catch (Exception exception) { session.failed(active, exception); throw exception; }
                    }
                    indicator.setFraction(1.0); appendLog("批量任务完成，共生成 " + completed + " 套全链路资产。\n");
                } catch (Exception exception) { fail(PipelineStage.REQUIREMENT_TO_TAD, exception); } finally { setRunning(false); }
            }
        });
    }

    private ExecutionSnapshot snapshotExecutionState() {
        WorkspaceConfig config; try { config = workspace.load(); } catch (IOException exception) { config = new WorkspaceConfig(); }
        AssetPlan plan = new AssetPlan(currentPlan.assetId(), currentPlan.sourcePath(), tadPath.getText().trim(), testcasePath.getText().trim(),
                apiSpecPath.getText().trim(), currentPlan.webAtcPath(), currentPlan.feedbackPath(), currentPlan.manifestPath());
        List<String> inputs = currentSources.stream().filter(java.nio.file.Files::isRegularFile)
                .filter(path -> path.toAbsolutePath().normalize().startsWith(projectRoot()))
                .map(inputAssets::relative).toList();
        EndpointProtocol protocol = selectedProtocol();
        EndpointSelectorKind kind = selectedSelectorKind();
        String value = selectorValue.getText().trim();
        String interfaceContext;
        List<DiscoveredEndpoint> discoveredEndpoints;
        if (value.isBlank()) {
            interfaceContext = "{\n  \"schemaVersion\": 1,\n  \"kind\": \"interface-discovery-evidence\",\n"
                    + "  \"status\": \"SELECTOR_REQUIRED\",\n  \"protocol\": \"" + protocol
                    + "\",\n  \"selectorKind\": \"" + kind
                    + "\",\n  \"evidenceRule\": \"No interface selector was supplied; do not invent an endpoint, Java type or call chain.\"\n}";
            discoveredEndpoints = List.of();
        } else {
            InterfaceEvidenceService.Evidence discovered = interfaceEvidence.collect(protocol, kind, value);
            interfaceContext = discovered.json();
            discoveredEndpoints = discovered.endpoints();
        }
        return new ExecutionSnapshot(mergeEvidence(evidence.getText(), interfaceContext), "",
                List.copyOf(imageAttachments), inputs, plan, config, protocol, kind, value,
                discoveredEndpoints);
    }

    private ExecutionSnapshot prepareSnapshot(ExecutionSnapshot requested, String sourceContent) throws IOException {
        workspace.initialize();
        WorkspaceConfig config = workspace.load();
        AssetPlan plan = requested.plan();
        List<String> inputs = requested.inputPaths();
        Path primary = store.resolve(plan.sourcePath());
        if (inputs.isEmpty() || !java.nio.file.Files.isRegularFile(primary)) {
            Path managed = inputAssets.registerManualRequirement(sourceContent, config);
            plan = AssetPlan.forSource(projectRoot(), managed, config);
            inputs = List.of(inputAssets.relative(managed));
            AssetPlan resolvedPlan = plan;
            ApplicationManager.getApplication().invokeLater(() -> {
                currentSource = managed;
                currentSources = List.of(managed);
                currentPlan = resolvedPlan;
                sourceLabel.setText(inputAssets.relative(managed));
                tadPath.setText(resolvedPlan.tadPath());
                testcasePath.setText(resolvedPlan.testcasePath());
                apiSpecPath.setText(resolvedPlan.apiSpecPath());
            });
        }
        Path evidenceAsset = inputAssets.registerEvidence(plan.assetId(), requested.evidence(), config);
        String evidencePath = evidenceAsset == null ? "" : inputAssets.relative(evidenceAsset);
        return new ExecutionSnapshot(requested.evidence(), evidencePath, requested.images(), inputs, plan, config,
                requested.protocol(), requested.selectorKind(), requested.selectorValue(), requested.discoveredEndpoints());
    }
    private TraceabilityService.RunSession beginSession(ExecutionSnapshot snapshot, String source) throws IOException {
        var settings = AiTestFlowSettings.getInstance(project).data();
        return traceability.begin(snapshot.plan(), source, snapshot.inputPaths(), snapshot.evidencePath(),
                settings.demoMode ? "GOLDEN_DEMO" : "MODEL",
                settings.demoMode ? "fixture:" + settings.goldenDataset : settings.model, snapshot.config());
    }
    private String inputFor(PipelineStage stage) {
        return switch (stage) {
            case REQUIREMENT_TO_TAD -> requirement.getText();
            case TAD_TO_TEST_CASES -> editors.get(PipelineStage.REQUIREMENT_TO_TAD).getText();
            case TEST_CASES_TO_WEB_ATC, TEST_CASES_TO_API_SPEC -> editors.get(PipelineStage.TAD_TO_TEST_CASES).getText();
            case FEEDBACK -> evidence.getText();
        };
    }
    private void alignSelectorWithProtocol() {
        EndpointProtocol protocol = selectedProtocol();
        EndpointSelectorKind current = selectedSelectorKind();
        EndpointSelectorKind[] allowed = protocol == EndpointProtocol.DUBBO
                ? new EndpointSelectorKind[]{EndpointSelectorKind.DUBBO_METHOD,
                EndpointSelectorKind.JAVA_SYMBOL, EndpointSelectorKind.API_CODE}
                : new EndpointSelectorKind[]{EndpointSelectorKind.HTTP_PATH,
                EndpointSelectorKind.JAVA_SYMBOL, EndpointSelectorKind.API_CODE};
        selectorKind.setModel(new DefaultComboBoxModel<>(allowed));
        boolean preserve = Arrays.asList(allowed).contains(current);
        selectorKind.setSelectedItem(preserve ? current : allowed[0]);
    }
    private boolean hasInterfaceSelector() { return !selectorValue.getText().trim().isBlank(); }
    private void warnMissingInterfaceSelector() {
        Messages.showWarningDialog(project,
                "请先填写接口编码、HTTP 路径、Java 方法或 Dubbo 方法。该值会作为接口发现与脚手架生成的可追溯输入。",
                "AI Test Flow");
    }
    private EndpointProtocol selectedProtocol() {
        EndpointProtocol selected = (EndpointProtocol) interfaceProtocol.getSelectedItem();
        return selected == null ? EndpointProtocol.HTTP : selected;
    }
    private EndpointSelectorKind selectedSelectorKind() {
        EndpointSelectorKind selected = (EndpointSelectorKind) selectorKind.getSelectedItem();
        return selected == null ? EndpointSelectorKind.API_CODE : selected;
    }
    private InterfaceEvidenceService.Evidence collectInterfaceEvidence() {
        return interfaceEvidence.collect(selectedProtocol(), selectedSelectorKind(), selectorValue.getText());
    }
    private static String mergeEvidence(String userEvidence, String interfaceEvidence) {
        String user = userEvidence == null ? "" : userEvidence.trim();
        if (user.isBlank()) return "【接口发现证据】\n" + interfaceEvidence;
        return user + "\n\n【接口发现证据】\n" + interfaceEvidence;
    }
    private void openOutputDirectory() {
        try { Path directory = store.resolve(testcasePath.getText()).getParent(); if (directory != null) { java.nio.file.Files.createDirectories(directory); RevealFileAction.openDirectory(directory.toFile()); } }
        catch (Exception exception) { fail(PipelineStage.REQUIREMENT_TO_TAD, exception); }
    }
    private void openScaffoldManifest() {
        if (lastScaffoldManifest == null) { info("请先生成接口自动化脚手架，生成清单会列出 Java 和用例数据文件。"); return; }
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(lastScaffoldManifest);
        if (file != null) FileEditorManager.getInstance(project).openFile(file, true);
        else info("生成清单不存在，请检查项目文件或重新生成。");
    }
    private void fail(PipelineStage stage, Exception exception) {
        LOG.warn("AI Test Flow stage failed: " + stage.name(), exception);
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(); appendLog("失败：" + stage.displayName() + " - " + message + "\n");
        ApplicationManager.getApplication().invokeLater(() -> {
            if (workbench != null) workbench.failure(message);
            Messages.showErrorDialog(project, message, "AI Test Flow");
        });
    }
    private void info(String message) {
        UiDispatch.run(() -> {
            if (!project.isDisposed()) Messages.showInfoMessage(project, message, "AI Test Flow");
        });
    }
    private void appendLog(String message) { ApplicationManager.getApplication().invokeLater(() -> { log.append(message); log.setCaretPosition(log.getDocument().getLength()); }); }
    private Path projectRoot() { return Path.of(project.getBasePath()).toAbsolutePath().normalize(); }
}
