package io.github.aitestflow.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/** Presentation shell independent of model, project IO and pipeline implementations. */
public final class EnterpriseWorkbench extends JPanel {
    private final JLabel mode = WorkbenchStyle.label("黄金集演练", 11, true);
    private final JLabel status = WorkbenchStyle.label("就绪", 12, false);
    private final JLabel detail = WorkbenchStyle.hint("选择输入材料，或使用黄金集验证流程");
    private final JProgressBar progress = new JProgressBar(0, 6);
    private final JPanel inspector = new JPanel(new BorderLayout());
    private final JPanel editor = new JPanel(new BorderLayout(0, 8));
    private final JSplitPane split;
    private final JButton run;
    private final JButton inspect;
    private final List<JComponent> taskControls = new ArrayList<>();
    private final List<JLabel> stepStates = new ArrayList<>();
    private final JPanel steps = WorkbenchStyle.column();
    private final JTabbedPane documents;
    private int completed;
    private int activeStep = -1;
    private boolean manualInspector;
    private boolean failed;

    public EnterpriseWorkbench(String projectName, JTabbedPane documents, JComponent configuration,
                               Runnable importAction, Runnable goldenAction, Runnable runAction,
                               Runnable settingsAction, Runnable openAssetsAction) {
        super(new BorderLayout(0, 14));
        this.documents = documents;
        setBorder(new EmptyBorder(18, 18, 12, 18));
        JPanel top = WorkbenchStyle.column();
        JPanel brand = new JPanel(new BorderLayout(8, 0)); brand.setOpaque(false);
        JPanel identity = WorkbenchStyle.column();
        identity.add(WorkbenchStyle.label("AI Test Flow", 22, true));
        identity.add(Box.createVerticalStrut(5));
        identity.add(WorkbenchStyle.hint("质量工程工作台  /  " + projectName));
        brand.add(identity, BorderLayout.CENTER);
        mode.setBorder(new EmptyBorder(6, 10, 6, 10));
        mode.setForeground(WorkbenchStyle.accent());
        brand.add(mode, BorderLayout.EAST);
        top.add(brand); top.add(Box.createVerticalStrut(16));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); toolbar.setOpaque(false);
        run = WorkbenchStyle.action("运行完整链路", runAction);
        run.putClientProperty("workbench.primary", true);
        run.putClientProperty("JButton.buttonType", "default");
        run.setToolTipText("生成分析、用例、接口脚手架与反馈资产");
        toolbar.add(run); taskControls.add(run);
        JButton imports = WorkbenchStyle.action("导入材料", importAction);
        JButton golden = WorkbenchStyle.action("黄金集 Demo", goldenAction);
        toolbar.add(imports); toolbar.add(golden);
        taskControls.add(imports); taskControls.add(golden);
        JButton settings = WorkbenchStyle.action("模型设置", settingsAction);
        toolbar.add(settings); taskControls.add(settings);
        // Toolbar wraps naturally on narrow tool windows rather than clipping actions.
        top.add(wrappingToolbar(toolbar)); top.add(Box.createVerticalStrut(12));
        progress.setPreferredSize(new Dimension(80, 3)); progress.setBorderPainted(false);
        progress.setStringPainted(false); progress.setForeground(WorkbenchStyle.accent());
        top.add(progress); add(top, BorderLayout.NORTH);

        documents.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        documents.setBorder(new EmptyBorder(0, 0, 0, 0));
        documents.setMinimumSize(new Dimension(220, 160));
        editor.setOpaque(false);
        JPanel editorHeader = new JPanel(new BorderLayout()); editorHeader.setOpaque(false);
        editorHeader.add(WorkbenchStyle.label("项目资产", 14, true), BorderLayout.WEST);
        inspect = WorkbenchStyle.action("流程与配置", () -> {
            manualInspector = !inspector.isVisible(); setInspectorVisible(manualInspector);
        });
        inspect.setToolTipText("显示或隐藏流程步骤、接口目标及输出路径");
        editorHeader.add(inspect, BorderLayout.EAST);
        editor.add(editorHeader, BorderLayout.NORTH); editor.add(documents, BorderLayout.CENTER);

        JTabbedPane sideTabs = new JTabbedPane();
        sideTabs.addTab("流程", new JScrollPane(steps));
        JScrollPane configScroll = new JScrollPane(configuration);
        configScroll.setBorder(null); configScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sideTabs.addTab("配置", configScroll);
        JPanel sideHeading = new JPanel(new BorderLayout()); sideHeading.setOpaque(false);
        sideHeading.setBorder(new EmptyBorder(0, 8, 8, 0));
        sideHeading.add(WorkbenchStyle.label("流程与配置", 13, true), BorderLayout.WEST);
        JButton close = WorkbenchStyle.action("收起", () -> {
            manualInspector = false; setInspectorVisible(false);
        });
        sideHeading.add(close, BorderLayout.EAST);
        inspector.add(sideHeading, BorderLayout.NORTH);
        inspector.add(sideTabs, BorderLayout.CENTER);
        inspector.setMinimumSize(new Dimension(220, 0));
        inspector.setPreferredSize(new Dimension(280, 400));
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, inspector);
        split.setBorder(null); split.setDividerSize(6); split.setResizeWeight(1.0);
        split.setContinuousLayout(true); add(split, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0)); footer.setOpaque(false);
        JPanel state = WorkbenchStyle.column(); state.add(status); state.add(Box.createVerticalStrut(3)); state.add(detail);
        footer.add(state, BorderLayout.CENTER);
        JButton assets = WorkbenchStyle.action("打开产物", openAssetsAction);
        footer.add(assets, BorderLayout.EAST); add(footer, BorderLayout.SOUTH);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                setInspectorVisible(manualInspector || getWidth() >= 820);
            }
        });
    }

    private JPanel wrappingToolbar(JPanel toolbar) {
        // Two rows at small widths; actual controls retain native keyboard/focus behavior.
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override public Dimension getPreferredSize() {
                int available = Math.max(220, EnterpriseWorkbench.this.getWidth() - 36);
                int rows = Math.max(1, (toolbar.getPreferredSize().width + available - 1) / available);
                return new Dimension(100, rows * 42);
            }
        };
        wrapper.setOpaque(false); wrapper.add(toolbar); return wrapper;
    }

    public void addStep(String title, String description, int documentIndex, Runnable action) {
        addStep(title, description, documentIndex, action, () -> documents.setSelectedIndex(documentIndex));
    }

    public void addStep(String title, String description, int documentIndex, Runnable action, Runnable viewAction) {
        int index = stepStates.size();
        JPanel item = new JPanel(new BorderLayout(8, 6)); item.setOpaque(false);
        item.setBorder(new EmptyBorder(14, 12, 14, 12));
        JLabel badge = WorkbenchStyle.label(String.format("%02d", index + 1), 14, true);
        badge.setForeground(WorkbenchStyle.accent()); item.add(badge, BorderLayout.WEST);
        JPanel body = WorkbenchStyle.column();
        body.add(WorkbenchStyle.label(title, 13, true)); body.add(Box.createVerticalStrut(4));
        body.add(WorkbenchStyle.hint(description)); body.add(Box.createVerticalStrut(8));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)); actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton execute = WorkbenchStyle.action("生成", action); taskControls.add(execute);
        JButton view = WorkbenchStyle.action("查看", viewAction);
        JLabel stageState = WorkbenchStyle.hint("待生成"); stepStates.add(stageState);
        actions.add(execute); actions.add(view); body.add(actions);
        body.add(Box.createVerticalStrut(6)); body.add(stageState);
        item.add(body, BorderLayout.CENTER); item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        item.setAlignmentX(Component.LEFT_ALIGNMENT); steps.add(item);
        steps.add(new JSeparator());
    }

    private void setInspectorVisible(boolean visible) {
        boolean fullPanel = visible && getWidth() < 820;
        editor.setVisible(!fullPanel);
        inspector.setVisible(visible); split.setDividerSize(visible ? 6 : 0);
        if (visible) split.setDividerLocation(fullPanel ? 0 : Math.max(220, getWidth() - 320));
        inspect.setText(visible ? "收起面板" : "流程与配置");
        revalidate();
    }

    public void setMode(boolean golden, String model) {
        mode.setText(golden ? "黄金集演练" : "模型 · " + model);
        mode.setToolTipText(golden ? "固定响应 · 无需联网 · order-create-v1" : "使用项目配置的模型网关");
    }
    public void setBusy(boolean busy) {
        taskControls.forEach(control -> control.setEnabled(!busy));
        progress.setIndeterminate(busy);
        if (busy) { failed = false; status.setText("正在处理"); status.setForeground(WorkbenchStyle.accent()); }
        else if (!failed) {
            status.setText(completed > 0 ? "本次资产已写入项目" : "就绪");
            status.setForeground(UIManager.getColor("Label.foreground"));
        }
        run.setText(busy ? "生成中…" : "运行完整链路");
    }
    public void stage(int index, String state, String message) {
        if (index >= 0 && index < stepStates.size()) {
            stepStates.get(index).setText(state);
            if ("处理中…".equals(state)) activeStep = index;
            if ("已生成".equals(state)) {
                if (activeStep == index) activeStep = -1;
                completed = (int) stepStates.stream().filter(label -> "已生成".equals(label.getText())).count();
                progress.setValue(completed);
            }
        }
        detail.setText(message); detail.setToolTipText(message);
    }
    public void failure(String message) {
        failed = true; status.setText("处理未完成 · 查看运行日志");
        if (activeStep >= 0) stepStates.get(activeStep).setText("未完成");
        status.setForeground(WorkbenchStyle.color("Component.errorFocusColor", new Color(205, 82, 82)));
        detail.setText(message); detail.setToolTipText(message);
    }
    public void resetProgress() {
        completed = 0; activeStep = -1; failed = false; progress.setValue(0);
        stepStates.forEach(label -> label.setText("待生成"));
        status.setText("就绪"); detail.setText("输入与产物均保存在当前项目，通过 Git 管理");
    }
}
