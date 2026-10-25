package io.testkit.basetest.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JsonLinesResultPublisherTest {
  @Test
  public void enterpriseFieldsAreWhitelistedAndUntrustedMetadataIsOmitted() throws Exception {
    Path file = Files.createTempFile("team-report", ".jsonl");
    try {
      var publisher = new JsonLinesResultPublisher(file);
      publisher.publish(
          new TestResultRecord(
              "run",
              "invocation",
              "name",
              TestResultRecord.Status.PASSED,
              Instant.now(),
              1,
              null,
              Map.of(
                  "logicalCaseId",
                  "orders.one",
                  "team",
                  "orders",
                  "workerId",
                  "worker-a",
                  "environment",
                  "qa",
                  "shardIndex",
                  0,
                  "shardCount",
                  2,
                  "owner",
                  "secret\nvalue",
                  "password",
                  "private-secret")));
      var json = new ObjectMapper().readTree(Files.readString(file));
      Assert.assertEquals(json.path("logicalCaseId").asText(), "orders.one");
      Assert.assertEquals(json.path("shardCount").asInt(), 2);
      Assert.assertFalse(json.has("owner"));
      Assert.assertFalse(json.has("password"));
      Assert.assertFalse(Files.readString(file).contains("private-secret"));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  public void fanOutContinuesAfterFailure() {
    var memory = new InMemoryResultPublisher();
    var publisher =
        new CompositeResultPublisher(
            r -> {
              throw new IllegalStateException("broken");
            },
            memory);
    Assert.expectThrows(
        IllegalStateException.class,
        () ->
            publisher.publish(
                new TestResultRecord(
                    "r",
                    "c",
                    "case",
                    TestResultRecord.Status.PASSED,
                    Instant.now(),
                    0,
                    null,
                    Map.of())));
    Assert.assertEquals(memory.results().size(), 1);
  }

  @Test
  public void concurrentWritesAreValidAndSensitivePayloadsAreOmitted() throws Exception {
    Path file = Files.createTempFile("test-report", ".jsonl");
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      var publisher = new JsonLinesResultPublisher(file);
      var futures = new java.util.ArrayList<Future<?>>();
      for (int i = 0; i < 40; i++) {
        int id = i;
        futures.add(
            pool.submit(
                () ->
                    publisher.publish(
                        new TestResultRecord(
                            "r",
                            "c" + id,
                            "case",
                            TestResultRecord.Status.FAILED,
                            Instant.now(),
                            10,
                            "password=secret",
                            Map.of("token", "secret")))));
      }
      for (var future : futures) future.get();
      publisher.accept(
          new InvocationRecord(
              Instant.now(),
              "http",
              "secret",
              Map.of("password", "secret"),
              "secret",
              2,
              "secret"));
      publisher.complete(new TestRunSummary("r", Instant.now(), Instant.now(), 0, 40, 0));
      var lines = Files.readAllLines(file);
      Assert.assertEquals(lines.size(), 42);
      for (String line : lines) Assert.assertTrue(new ObjectMapper().readTree(line).has("type"));
      Assert.assertFalse(Files.readString(file).contains("secret"));
    } finally {
      pool.shutdownNow();
      Files.deleteIfExists(file);
    }
  }
}
