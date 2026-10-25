package com.example.automation.bootstrap;
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
