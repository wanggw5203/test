package io.github.aitestflow.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import io.github.aitestflow.ui.WorkbenchStyle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Box;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import javax.swing.border.EmptyBorder;
import java.util.Objects;

public final class AiTestFlowConfigurable implements Configurable {
    private final Project project;
    private JPanel panel;
    private JBTextField baseUrl;
    private JBTextField model;
    private JBPasswordField apiKey;
    private JBCheckBox demoMode;

    public AiTestFlowConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "AI Test Flow";
    }

    @Override
    public @Nullable JComponent createComponent() {
        var data = AiTestFlowSettings.getInstance(project).data();
        baseUrl = new JBTextField(data.baseUrl);
        model = new JBTextField(data.model);
        apiKey = new JBPasswordField();
        apiKey.setText(ApiKeyStore.get(project));
        demoMode = new JBCheckBox("黄金集 Demo（默认开启，无需模型和 API Key）", data.demoMode);

        JPanel content = WorkbenchStyle.column();
        content.setBorder(new EmptyBorder(20, 20, 20, 20));
        content.add(WorkbenchStyle.label("模型与演练", 22, true));
        content.add(Box.createVerticalStrut(6));
        content.add(WorkbenchStyle.hint("统一配置所有分析与生成阶段的模型入口"));
        content.add(Box.createVerticalStrut(22));
        JPanel simulation = WorkbenchStyle.column();
        demoMode.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); simulation.add(demoMode);
        simulation.add(Box.createVerticalStrut(10));
        simulation.add(new JBLabel("<html>黄金集 order-create-v1 · 正常 / 边界 / 必填校验<br>固定响应，无需网络与密钥。适合首次验证整套流程。</html>"));
        content.add(WorkbenchStyle.card("运行模式", "默认使用黄金集演练", simulation));
        content.add(Box.createVerticalStrut(16));
        JPanel gateway = WorkbenchStyle.column();
        WorkbenchStyle.field(gateway, "模型网关 · Base URL", baseUrl);
        WorkbenchStyle.field(gateway, "模型名称", model);
        WorkbenchStyle.field(gateway, "API Key", apiKey);
        gateway.add(WorkbenchStyle.hint("密钥保存在 IDEA 密码库，资产写入当前项目。"));
        content.add(WorkbenchStyle.card("企业模型网关", "关闭黄金集后生效 · 兼容 Chat Completions", gateway));
        content.add(Box.createVerticalStrut(16));
        content.add(WorkbenchStyle.hint("输入材料、TAD、用例、自动化产物由项目 Git 统一维护。"));
        demoMode.addActionListener(event -> updateModeControls());
        updateModeControls();
        panel = new JPanel(new BorderLayout());
        JScrollPane scroll = new JScrollPane(content); scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void updateModeControls() {
        boolean enabled = !demoMode.isSelected();
        baseUrl.setEnabled(enabled); model.setEnabled(enabled); apiKey.setEnabled(enabled);
    }

    @Override
    public boolean isModified() {
        var data = AiTestFlowSettings.getInstance(project).data();
        return !Objects.equals(baseUrl.getText().trim(), data.baseUrl)
                || !Objects.equals(model.getText().trim(), data.model)
                || demoMode.isSelected() != data.demoMode
                || !Objects.equals(new String(apiKey.getPassword()), ApiKeyStore.get(project));
    }

    @Override
    public void apply() {
        var data = AiTestFlowSettings.getInstance(project).data();
        data.baseUrl = baseUrl.getText().trim();
        data.model = model.getText().trim();
        data.demoMode = demoMode.isSelected();
        data.goldenDataset = "order-create-v1";
        ApiKeyStore.set(project, new String(apiKey.getPassword()));
    }

    @Override
    public void reset() {
        var data = AiTestFlowSettings.getInstance(project).data();
        baseUrl.setText(data.baseUrl);
        model.setText(data.model);
        apiKey.setText(ApiKeyStore.get(project));
        demoMode.setSelected(data.demoMode);
        updateModeControls();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }
}
