package io.github.aitestflow.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import io.github.aitestflow.initializer.ProjectOptions;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/** Only collects inputs; templates are owned by the independent initializer module. */
final class ProjectInitializationDialog extends DialogWrapper {
    private final JComboBox<ProjectOptions.Type> type = new JComboBox<>(ProjectOptions.Type.values());
    private final JBTextField directory;
    private final JBTextField group = new JBTextField("com.example");
    private final JBTextField artifact = new JBTextField("api-automation");
    private final JBTextField packageName = new JBTextField("com.example.automation");
    private final JBTextField framework = new JBTextField("1.1.0-SNAPSHOT");

    ProjectInitializationDialog(Project project, Path defaultDirectory) {
        super(project);
        directory = new JBTextField(defaultDirectory.toString(), 48);
        setTitle("创建自动化项目");
        setOKButtonText("生成项目");
        init();
    }
    @Override protected @Nullable JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("项目类型", type)
                .addLabeledComponent("目标目录（新目录或无冲突目录）", directory)
                .addLabeledComponent("Maven groupId", group)
                .addLabeledComponent("Maven artifactId", artifact)
                .addLabeledComponent("Java 根包名", packageName)
                .addLabeledComponent("自动化框架版本", framework)
                .addComponent(new com.intellij.ui.components.JBLabel("仅生成工程；不覆盖已有构建，不安装依赖，不连接真实服务。"))
                .getPanel();
    }
    @Override protected @Nullable ValidationInfo doValidate() {
        try {
            if (directory.getText().isBlank() || !target().isAbsolute()) return new ValidationInfo("请填写目标目录的绝对路径", directory);
            options(); return null;
        } catch (RuntimeException error) { return new ValidationInfo(error.getMessage()); }
    }
    Path target() { return Path.of(directory.getText().trim()).normalize(); }
    ProjectOptions options() {
        return new ProjectOptions((ProjectOptions.Type) type.getSelectedItem(), group.getText().trim(),
                artifact.getText().trim(), packageName.getText().trim(), framework.getText().trim());
    }
}
