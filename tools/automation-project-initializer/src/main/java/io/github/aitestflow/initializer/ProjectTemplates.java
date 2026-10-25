package io.github.aitestflow.initializer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned initialization rules, deliberately separate from interface/case generation rules. */
final class ProjectTemplates {
    static Map<String, String> render(ProjectOptions o) {
        Map<String, String> files = new LinkedHashMap<>();
        String pkg = o.packageName();
        String main = "src/main/java/" + pkg.replace('.', '/') + "/";
        String test = "src/test/java/" + pkg.replace('.', '/') + "/smoke/";
        String data = "src/test/resources/" + pkg.replace('.', '/') + "/smoke/";
        String dubbo = o.type() == ProjectOptions.Type.DUBBO ? """
              <dependency><groupId>io.testkit</groupId><artifactId>enterprise-adapters</artifactId><version>${testkit.version}</version></dependency>
              <dependency><groupId>org.apache.dubbo</groupId><artifactId>dubbo</artifactId><version>3.3.5</version></dependency>
            """ : "";
        files.put("pom.xml", """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>%s</groupId><artifactId>%s</artifactId><version>1.0-SNAPSHOT</version>
              <properties><maven.compiler.release>21</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding><testkit.version>%s</testkit.version></properties>
              <dependencies>
                <dependency><groupId>io.testkit</groupId><artifactId>base-test-toolkit</artifactId><version>${testkit.version}</version></dependency>
            %s
              </dependencies>
              <build><plugins>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version><configuration><parameters>true</parameters><proc>none</proc></configuration></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.2.5</version></plugin>
              </plugins></build>
            </project>
            """.formatted(o.groupId(), o.artifactId(), o.frameworkVersion(), dubbo));
        files.put(".gitignore", "target/\n.idea/\n*.iml\n.env\n# Inputs and quality assets must remain versioned.\n");
        files.put(".ai-test-flow/automation-project.yaml", """
            schemaVersion: 1
            initializerVersion: 1.0.1
            projectType: %s
            framework: io.testkit:base-test-toolkit
            frameworkVersion: %s
            frameworkScope: compile
            basePackage: %s
            mainSourceRoot: src/main/java
            testSourceRoot: src/test/java
            testResourceRoot: src/test/resources
            """.formatted(o.type(), o.frameworkVersion(), pkg));
        for (String environment : java.util.List.of("local", "test", "pre")) {
            files.put("src/main/resources/environments/" + environment + ".properties", """
                # Non-secret defaults only. Override using JVM -D properties or your secret manager.
                project.type=%s
                api.baseUrl=http://127.0.0.1:8080/
                dubbo.registry=
                dubbo.directUrl=
                testkit.report.file=target/test-results.jsonl
                # Diagnostic call logs: redacted bodies are separate from payload-free result reports.
                testkit.log.enabled=true
                testkit.log.console=true
                testkit.log.bodies=%s
                testkit.log.file=target/logs/api-calls-${pid}.jsonl
                testkit.log.maxBodyChars=4096
                testkit.log.maxFileBytes=10485760
                """.formatted(o.type(), environment.equals("pre") ? "false" : "true"));
        }
        files.put(main + "config/ProjectSettings.java", """
            package %s.config;
            import java.io.InputStream;
            import java.util.Map;
            import java.util.LinkedHashMap;
            import java.util.Properties;
            public final class ProjectSettings {
                private static final Map<String,String> defaults = new LinkedHashMap<>();
                private ProjectSettings() {}
                public static synchronized void load() {
                    String env = System.getProperty("test.env", System.getenv().getOrDefault("TEST_ENV", "local"));
                    if (!env.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid test.env");
                    String resource = "environments/" + env + ".properties";
                    try (InputStream in = ProjectSettings.class.getClassLoader().getResourceAsStream(resource)) {
                        if (in == null) throw new IllegalStateException("Environment configuration not found: " + resource);
                        Properties values = new Properties(); values.load(in);
                        values.forEach((key, value) -> {
                            String name = key.toString();
                            if (System.getProperty(name) == null) {
                                System.setProperty(name, value.toString()); defaults.put(name, value.toString());
                            }
                        });
                    } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot load " + resource, failure); }
                }
                public static String value(String key) {
                    String value = System.getProperty(key);
                    if (value == null) throw new IllegalStateException("Project is not initialized or setting is missing: " + key);
                    return value;
                }
                public static synchronized void clearDefaults() {
                    defaults.forEach((key,value) -> { if (value.equals(System.getProperty(key))) System.clearProperty(key); });
                    defaults.clear();
                }
            }
            """.formatted(pkg));
        files.put(main + "bootstrap/ProjectBootstrap.java", """
            package %s.bootstrap;
            import %s.config.ProjectSettings;
            import io.testkit.basetest.runtime.*;
            import java.nio.file.Path;
            import org.testng.IExecutionListener;
            /** Project-owned composition root. Add real identity/DataFactory providers here. */
            public final class ProjectBootstrap implements IExecutionListener {
                private AutomationRuntime previous;
                @Override public void onExecutionStart() {
                    ProjectSettings.load();
                    previous = RuntimeRegistry.current();
                    JsonLinesResultPublisher report = new JsonLinesResultPublisher(Path.of(ProjectSettings.value("testkit.report.file")));
                    // Unsupported business capabilities remain explicit, not simulated as configured.
                    RuntimeRegistry.install(new AutomationRuntime(null, null, report, null, report, null, null, null));
                }
                @Override public void onExecutionFinish() {
                    try { if (previous != null) RuntimeRegistry.install(previous); }
                    finally { ProjectSettings.clearDefaults(); }
                }
            }
            """.formatted(pkg, pkg));
        files.put("src/main/resources/META-INF/services/org.testng.ITestNGListener", pkg + ".bootstrap.ProjectBootstrap\n");
        files.put(main + "bootstrap/AutomationMain.java", """
            package %s.bootstrap;
            import java.util.List;
            import org.testng.TestNG;
            /** Optional launcher; use the test runtime classpath. Normal entry: mvn test. */
            public final class AutomationMain {
                public static void main(String[] args) {
                    TestNG runner = new TestNG();
                    runner.setTestSuites(List.of(args.length == 0 ? "src/test/resources/testng.xml" : args[0]));
                    runner.run();
                    if (runner.hasFailure() || runner.hasSkip()) System.exit(1);
                }
            }
            """.formatted(pkg));
        files.put(test + "InitializationSmokeTest.java", """
            package %s.smoke;
            import io.testkit.basetest.BaseTest;
            import io.testkit.basetest.data.CaseContext;
            import io.testkit.basetest.data.DataDriveUtil;
            import %s.config.ProjectSettings;
            import java.lang.reflect.Method;
            import java.util.Map;
            import org.testng.Assert;
            import org.testng.annotations.DataProvider;
            import org.testng.annotations.Test;
            /** Checks bootstrap + YAML/JSON wiring, not real business API availability. */
            public class InitializationSmokeTest extends BaseTest {
                @DataProvider(name = "smoke") public Object[][] cases(Method method) {
                    return DataDriveUtil.loadTestData(method, "initialization-smoke");
                }
                @Test(dataProvider = "smoke") public void verifiesInitialization(CaseContext context, Map<String,Object> request, Map<String,Object> expected) {
                    Assert.assertEquals(currentCase().name(), context.name());
                    Assert.assertEquals(request.get("message"), expected.get("message"));
                    Assert.assertEquals(ProjectSettings.value("project.type"), "%s");
                }
            }
            """.formatted(pkg, pkg, o.type()));
        files.put(data + "initialization-smoke.yaml", """
            testDataSuite:
              cases:
                - name: initialization-only-no-business-call
                  thought: Verify project bootstrap and framework data loading only
                  request: [smoke-request.json]
                  expect: [smoke-expect.json]
            """);
        files.put(data + "smoke-request.json", "{\"message\":\"framework-ready\"}\n");
        files.put(data + "smoke-expect.json", "{\"message\":\"framework-ready\"}\n");
        files.put("src/test/resources/testng.xml", """
            <!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
            <suite name="%s"><test name="Project tests"><packages><package name="%s.*"/></packages></test></suite>
            """.formatted(o.artifactId(), pkg));
        if (o.type() == ProjectOptions.Type.HTTP) {
            files.put(main + "api/HttpApi.java", """
                package %s.api;
                import io.testkit.basetest.http.ApiClient;
                import io.testkit.basetest.http.ApiResponse;
                import %s.config.ProjectSettings;
                import java.util.Map;
                /** Base transport; generate business-specific API classes through the plugin. */
                public final class HttpApi {
                    public ApiResponse invoke(String method, String path, Map<String,String> headers, Object body) {
                        return new ApiClient(ProjectSettings.value("api.baseUrl")).request(method, path, headers, body);
                    }
                }
                """.formatted(pkg, pkg));
        } else if (o.type() == ProjectOptions.Type.DUBBO) {
            files.put(main + "api/ProjectDubboClient.java", """
                package %s.api;
                import io.testkit.adapters.dubbo.DubboClient;
                import %s.config.ProjectSettings;
                import java.time.Duration;
                import java.util.Map;
                public final class ProjectDubboClient implements AutoCloseable {
                    private final DubboClient client;
                    public ProjectDubboClient(String interfaceName, String group, String version) {
                        String registry = ProjectSettings.value("dubbo.registry");
                        String direct = ProjectSettings.value("dubbo.directUrl");
                        if (registry.isBlank() && direct.isBlank()) throw new IllegalStateException("Configure dubbo.registry or dubbo.directUrl before real invocation");
                        client = new DubboClient("%s", interfaceName, group, version,
                                registry.isBlank() ? null : registry, direct.isBlank() ? null : direct, Duration.ofSeconds(10));
                    }
                    public Object invoke(String method, String[] types, Object[] args, Map<String,String> attachments) {
                        return client.invoke(method, types, args, attachments);
                    }
                    @Override public void close() { client.close(); }
                }
                """.formatted(pkg, pkg, o.artifactId()));
        } else {
            files.put(main + "sdk/SdkAdapter.java", """
                package %s.sdk;
                /** Bind a verified SDK client supplied by your real SDK dependency; no fake SDK is created. */
                public final class SdkAdapter<T> {
                    private final T client;
                    public SdkAdapter(T client) { this.client = java.util.Objects.requireNonNull(client); }
                    public T client() { return client; }
                }
                """.formatted(pkg));
        }
        files.put(main + "data/README.md", "Business data preparation and cleanup extensions belong here. Do not store credentials.\n");
        files.put("quality-assets/inputs/README.md", "Maintain versioned PRD and source inputs here.\n");
        files.put("README.md", """
            # %s — %s 自动化项目

            由独立 automation-project-initializer 1.0.1 初始化；插件的接口/用例生成规则与此工具独立维护。

            ## 启动

            使用 JDK 21。先将 io.testkit:base-test-toolkit:%s 发布到可访问的 Maven 仓库；Dubbo 类型同时需要 enterprise-adapters。
            当前模板不会携带框架源码、安装框架或伪造仓库地址。若框架只在本仓库中，可先在 baseTest 目录执行 mvn -DskipTests install。

            运行 `mvn test` 验证初始化；使用 `mvn test -Dtest.env=test` 切换环境。默认用例只验证启动、YAML/JSON 和 BaseTest 链路，不调用 HTTP、Dubbo 或 SDK 业务服务。
            `bootstrap.AutomationMain` 是可选 TestNG 启动入口，运行它时需包含 test 编译输出与资源；不应只用 main 类路径运行。
            `src/test/resources/testng.xml` 可用于 IDEA TestNG 运行；mvn test 使用标准 *Test/*Tests 命名发现测试类，不限定业务包。

            ## 分层

            - main：bootstrap、环境加载、公共传输封装与业务数据扩展。
            - test：测试方法、YAML 用例、JSON 测试数据。
            - .ai-test-flow/automation-project.yaml：项目类型、框架版本及目录约定，交给插件读取。
            - quality-assets：项目内维护需求、TAD、用例和接口证据，并用 Git 版本管理。工具不会自动 git init、commit 或 push。

            ProjectBootstrap 由 TestNG ServiceLoader 自动加载，安装 JSONL 报告和项目运行时；真实身份、造数和发现 Provider 由项目接入，不会默认声称可用。
            环境配置不包含密钥，JVM -D 参数优先。真实 Dubbo 调用前需设置 registry 或 directUrl；SDK 项目需补充真实 SDK Maven 坐标、客户端和生命周期适配。
            插件当前接口脚手架支持 HTTP/Dubbo；SDK 模板目前提供基础工程与通用适配扩展点，不代表已支持 SDK 方法自动发现和生成。

            在 IDEA 中打开本目录并导入 Maven 后，使用 AI Test Flow 插件继续 PRD → TAD → 测试用例 → 接口脚手架。
            """.formatted(o.artifactId(), o.type(), o.frameworkVersion()));
        return files;
    }
}
