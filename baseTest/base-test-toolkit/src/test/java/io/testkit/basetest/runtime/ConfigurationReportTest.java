package io.testkit.basetest.runtime;

import java.nio.file.*;
import org.testng.*;
import org.testng.annotations.*;

public class ConfigurationReportTest {
  static InMemoryResultPublisher memory;
  static JsonLinesResultPublisher report;

  @Test
  public void setupAndCleanupFailuresReachJsonlAndSummary() throws Exception {
    Path file = Files.createTempFile("configuration-report", ".jsonl");
    try {
      memory = new InMemoryResultPublisher();
      report = new JsonLinesResultPublisher(file);
      shared = new CompositeResultPublisher(memory, report);
      var runner = new TestNG();
      runner.setUseDefaultListeners(false);
      runner.setVerbose(0);
      runner.setTestClasses(new Class<?>[] {ConfigSetupFixture.class, ConfigCleanupFixture.class});
      runner.run();
      Assert.assertTrue(runner.hasFailure());
      var configurations =
          memory.results().stream()
              .filter(r -> "configuration".equals(r.metadata().get("phase")))
              .toList();
      Assert.assertEquals(configurations.size(), 2);
      Assert.assertTrue(
          configurations.stream().allMatch(r -> r.status() == TestResultRecord.Status.FAILED));
      Assert.assertEquals(memory.summary().failed(), 2);
      Assert.assertEquals(memory.summary().passed(), 1);
      Assert.assertEquals(memory.summary().skipped(), 1);
      var lines = Files.readAllLines(file);
      Assert.assertEquals(
          lines.stream().filter(l -> l.contains("\"phase\":\"configuration\"")).count(), 2L);
      Assert.assertTrue(lines.stream().noneMatch(l -> l.contains("private-secret")));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  static ResultPublisher shared;
}
