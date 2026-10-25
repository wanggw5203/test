package io.github.aitestflow.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Read-only handshake with the independently maintained initializer. */
final class AutomationProjectCheck {
    static void verify(Path root) throws IOException {
        boolean build = List.of("pom.xml", "build.gradle", "build.gradle.kts").stream()
                .anyMatch(name -> Files.isRegularFile(root.resolve(name)));
        if (!build) throw new IOException("当前目录不是已初始化的自动化模块：缺少 pom.xml 或 Gradle 构建文件。"
                + "请先点击“创建自动化项目（HTTP / Dubbo / SDK）”，或运行独立初始化工具。");
        Path contract = root.resolve(".ai-test-flow/automation-project.yaml");
        if (!Files.isRegularFile(contract)) {
            verifyDirectMavenDependency(root);
            return;
        }
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try {
            Object parsed = new Yaml(new SafeConstructor(options)).load(Files.readString(contract));
            if (!(parsed instanceof Map<?, ?> values) || !Integer.valueOf(1).equals(values.get("schemaVersion")))
                throw new IOException("automation-project.yaml 必须声明 schemaVersion: 1");
            if (!"io.testkit:base-test-toolkit".equals(values.get("framework")))
                throw new IOException("项目约定的 framework 必须是 io.testkit:base-test-toolkit");
            if (!"compile".equals(values.get("frameworkScope")))
                throw new IOException("main 下生成的接口/造数代码需要框架 compile 依赖，请由初始化工程配置。");
        } catch (org.yaml.snakeyaml.error.YAMLException error) {
            throw new IOException("项目约定 YAML 无效：" + error.getMessage(), error);
        }
    }

    private static void verifyDirectMavenDependency(Path root) throws IOException {
        Path pom = root.resolve("pom.xml");
        if (Files.isRegularFile(pom)) {
            try {
                var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                var document = factory.newDocumentBuilder().parse(pom.toFile());
                var xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
                var dependency = (org.w3c.dom.Node) xpath.evaluate(
                        "/project/dependencies/dependency[groupId='io.testkit' and artifactId='base-test-toolkit']",
                        document, javax.xml.xpath.XPathConstants.NODE);
                if (dependency != null) {
                    String scope = xpath.evaluate("scope", dependency).trim();
                    if (scope.isEmpty() || scope.equals("compile")) return;
                    throw new IOException("base-test-toolkit 必须是 compile 依赖，不能仅以 " + scope + " 引入；请由初始化工程调整。");
                }
            } catch (IOException error) { throw error; }
            catch (Exception error) { throw new IOException("无法读取现有 pom.xml", error); }
        }
        throw new IOException("无法确认框架 compile 依赖。若使用父 POM、属性或 Gradle，请由初始化工程提供 "
                + ".ai-test-flow/automation-project.yaml（schemaVersion: 1、framework: io.testkit:base-test-toolkit、frameworkScope: compile）。插件不会改动构建配置。");
    }
}
