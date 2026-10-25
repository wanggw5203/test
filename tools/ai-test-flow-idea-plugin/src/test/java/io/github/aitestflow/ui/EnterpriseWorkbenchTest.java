package io.github.aitestflow.ui;

import io.github.aitestflow.service.GoldenDemo;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EnterpriseWorkbenchTest {
    @Test
    void stageNavigationBusyStateAndFailureRemainUsableAtDesktopAndSidebarWidths() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger invoked = new AtomicInteger();
            JTabbedPane tabs = tabs();
            EnterpriseWorkbench shell = shell(tabs, invoked);
            shell.setSize(1100, 850); layout(shell);
            buttons(shell).stream().filter(b -> b.getText().equals("查看")).findFirst().orElseThrow().doClick();
            assertEquals(1, tabs.getSelectedIndex());
            JButton run = buttons(shell).stream().filter(b -> b.getText().equals("运行完整链路")).findFirst().orElseThrow();
            run.doClick(); assertEquals(1, invoked.get());
            shell.setBusy(true); assertFalse(run.isEnabled());
            shell.failure("输入规格未通过门禁"); shell.setBusy(false);
            assertTrue(run.isEnabled());
            assertTrue(labels(shell).stream().anyMatch(label -> label.getText().contains("处理未完成")));
            shell.setSize(440, 850); resize(shell); layout(shell);
            assertTrue(run.getBounds().width > 0);
            assertTrue(buttons(shell).stream().anyMatch(b -> b.getText().equals("流程与配置")));
            JButton model = buttons(shell).stream().filter(b -> b.getText().equals("模型设置")).findFirst().orElseThrow();
            assertTrue(model.getY() + model.getHeight() <= model.getParent().getHeight(), "Narrow toolbar must retain model settings");
            buttons(shell).stream().filter(b -> b.getText().equals("流程与配置")).findFirst().orElseThrow().doClick();
            layout(shell);
            buttons(shell).stream().filter(b -> b.getText().equals("收起")).findFirst().orElseThrow().doClick();
            layout(shell); assertTrue(tabs.isVisible());
        });
    }

    @Test
    void rendersDesktopSidebarAndDarkThemePreviewsFromActualWorkbenchComponents() throws Exception {
        Path directory = Path.of("build/ui-preview"); Files.createDirectories(directory);
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                UIManager.put("defaultFont", new Font("Microsoft YaHei UI", Font.PLAIN, 13));
                for (Object key : new ArrayList<>(UIManager.getDefaults().keySet())) {
                    if (key.toString().endsWith(".font")) UIManager.put(key, new Font("Microsoft YaHei UI", Font.PLAIN, 13));
                }
                render(directory.resolve("workbench-light.png"), 1100, 850);
                render(directory.resolve("workbench-sidebar.png"), 440, 900);
                UIManager.put("Panel.background", new Color(35, 37, 42));
                UIManager.put("Label.foreground", new Color(218, 221, 229));
                UIManager.put("Label.disabledForeground", new Color(146, 153, 168));
                UIManager.put("TextArea.background", new Color(29, 31, 36));
                UIManager.put("TextArea.foreground", new Color(218, 221, 229));
                UIManager.put("TabbedPane.background", new Color(43, 45, 51));
                UIManager.put("TabbedPane.foreground", new Color(218, 221, 229));
                UIManager.put("TabbedPane.selected", new Color(56, 60, 71));
                UIManager.put("Button.background", new Color(53, 57, 66));
                UIManager.put("Button.foreground", new Color(218, 221, 229));
                UIManager.put("Viewport.background", new Color(35, 37, 42));
                UIManager.put("Component.borderColor", new Color(67, 72, 83));
                UIManager.put("Component.focusColor", new Color(115, 157, 250));
                render(directory.resolve("workbench-dark.png"), 1100, 850);
            } catch (Exception exception) { throw new RuntimeException(exception); }
        });
        assertTrue(Files.size(directory.resolve("workbench-light.png")) > 10000);
    }

    private static JTabbedPane tabs() {
        JTabbedPane tabs = new JTabbedPane();
        for (String title : List.of("需求", "TAD / STD", "测试用例", "接口规格", "Web ATC", "反馈闭环", "证据", "运行日志")) {
            JTextArea text = new JTextArea(title.equals("需求") ? GoldenDemo.requirement() : "");
            text.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            text.setLineWrap(true); text.setWrapStyleWord(true);
            text.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            tabs.addTab(title, new JScrollPane(text));
        }
        return tabs;
    }
    private static EnterpriseWorkbench shell(JTabbedPane tabs, AtomicInteger invoked) {
        JPanel config = WorkbenchStyle.column();
        WorkbenchStyle.field(config, "接口协议", new JComboBox<>(new String[]{"HTTP", "DUBBO"}));
        WorkbenchStyle.field(config, "接口目标", new JTextField("POST /demo/orders"));
        EnterpriseWorkbench shell = new EnterpriseWorkbench("quality-knowledge-base", tabs, config,
                () -> {}, () -> {}, invoked::incrementAndGet, () -> {}, () -> {});
        shell.addStep("需求分析", "PRD → TAD / STD", 1, () -> {});
        shell.addStep("测试用例", "业务规则 → YAML 用例", 2, () -> {});
        shell.addStep("接口规格", "HTTP / Dubbo 契约", 3, () -> {});
        shell.addStep("接口自动化", "TestNG · YAML · JSON5", 3, () -> {});
        shell.addStep("Web 自动化", "页面证据 → Web ATC", 4, () -> {});
        shell.addStep("反馈与追溯", "执行证据 → 反馈报告", 5, () -> {});
        return shell;
    }
    private static void render(Path file, int width, int height) throws Exception {
        EnterpriseWorkbench shell = shell(tabs(), new AtomicInteger());
        shell.setMode(true, ""); shell.setSize(width, height); resize(shell); layout(shell);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); shell.printAll(graphics); graphics.dispose();
        ImageIO.write(image, "png", file.toFile());
    }
    private static void resize(EnterpriseWorkbench shell) {
        ComponentEvent event = new ComponentEvent(shell, ComponentEvent.COMPONENT_RESIZED);
        for (var listener : shell.getComponentListeners()) listener.componentResized(event);
    }
    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }
    private static List<JButton> buttons(Container root) { return find(root, JButton.class); }
    private static List<JLabel> labels(Container root) { return find(root, JLabel.class); }
    private static <T> List<T> find(Container root, Class<T> type) {
        List<T> results = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) results.add(type.cast(child));
            if (child instanceof Container nested) results.addAll(find(nested, type));
        }
        return results;
    }
}
