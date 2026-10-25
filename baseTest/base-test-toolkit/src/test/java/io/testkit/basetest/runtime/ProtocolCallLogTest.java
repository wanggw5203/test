package io.testkit.basetest.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testkit.basetest.data.CaseContext;
import io.testkit.basetest.data.CaseContextHolder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(singleThreaded = true)
public class ProtocolCallLogTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final class Settings implements AutoCloseable {
    final Map<String, String> old = new HashMap<>();

    Settings(Path file, boolean bodies) {
      set("testkit.log.enabled", "true");
      set("testkit.log.console", "false");
      set("testkit.log.bodies", Boolean.toString(bodies));
      set("testkit.log.file", file.toString());
    }

    void set(String key, String value) {
      if (!old.containsKey(key)) old.put(key, System.getProperty(key));
      System.setProperty(key, value);
    }

    public void close() {
      old.forEach(
          (key, value) -> {
            if (value == null) System.clearProperty(key);
            else System.setProperty(key, value);
          });
    }
  }

  public void redactsNestedCredentialsHeadersQueriesAndAssociatesTheCase() throws Exception {
    Path file = Files.createTempDirectory("call-redaction").resolve("calls.jsonl");
    try (var settings = new Settings(file, true);
        var trace = TraceContext.use(new TraceContext.Trace("run-test", "case-test"))) {
      settings.set("testkit.log.redactKeys", "voucher");
      CaseContextHolder.set(new CaseContext("CASE-001 / create", null, null, List.of(), Map.of()));
      try {
        var call =
            ProtocolCallLog.start(
                "HTTP",
                "http://user:RawUserPassword@localhost:18081/orders?token=RawQuerySecret",
                Map.of(
                    "method",
                    "POST",
                    "headers",
                    Map.of("Authorization", "Bearer RawHeaderSecret", "X-Source", "tests"),
                    "body",
                    Map.of(
                        "sku",
                        "SKU-001",
                        "nested",
                        List.of(
                            Map.of(
                                "password",
                                "RawBodySecret",
                                "access_token",
                                "RawAccessSecret",
                                "voucher",
                                "RawVoucherSecret")))));
        call.complete(
            Map.of(
                "status",
                200,
                "body",
                "{\"code\":\"OK\",\"token\":\"RawResponseSecret\",\"embedded\":\"{\\\"password\\\":\\\"RawEmbeddedSecret\\\"}\"}"),
            null);
      } finally {
        CaseContextHolder.clear();
      }
    }
    String output = Files.readString(file);
    for (String secret :
        List.of(
            "RawUserPassword",
            "RawQuerySecret",
            "RawHeaderSecret",
            "RawBodySecret",
            "RawAccessSecret",
            "RawVoucherSecret",
            "RawResponseSecret",
            "RawEmbeddedSecret")) Assert.assertFalse(output.contains(secret), secret);
    var rows = Files.readAllLines(file);
    Assert.assertEquals(rows.size(), 2);
    var request = JSON.readTree(rows.get(0));
    var response = JSON.readTree(rows.get(1));
    Assert.assertEquals(request.path("request").path("method").asText(), "POST");
    Assert.assertEquals(request.path("request").path("body").path("sku").asText(), "SKU-001");
    Assert.assertEquals(request.path("caseName").asText(), "CASE-001 / create");
    Assert.assertEquals(request.path("runId").asText(), "run-test");
    Assert.assertEquals(request.path("caseId").asText(), "case-test");
    Assert.assertEquals(request.path("callId"), response.path("callId"));
    Assert.assertEquals(response.path("response").path("body").path("code").asText(), "OK");
  }

  public void bodySwitchAndGlobalSwitchWork() throws Exception {
    Path file = Files.createTempDirectory("call-switch").resolve("calls.jsonl");
    try (var settings = new Settings(file, false)) {
      var call =
          ProtocolCallLog.start(
              "HTTP", "http://localhost/api", Map.of("body", Map.of("message", "HiddenBodyValue")));
      call.complete(Map.of("body", Map.of("message", "HiddenResponseValue")), null);
      Assert.assertFalse(Files.readString(file).contains("HiddenBodyValue"));
      Assert.assertFalse(Files.readString(file).contains("HiddenResponseValue"));
      long previous = Files.size(file);
      settings.set("testkit.log.enabled", "false");
      ProtocolCallLog.start("HTTP", "http://localhost/api", Map.of()).complete(Map.of(), null);
      Assert.assertEquals(Files.size(file), previous);
    }
  }

  public void truncationRotationAndUntrustedTextStaySafe() throws Exception {
    Path file = Files.createTempDirectory("call-limits").resolve("calls.jsonl");
    try (var settings = new Settings(file, true)) {
      settings.set("testkit.log.maxBodyChars", "128");
      settings.set("testkit.log.maxFileBytes", "2048");
      for (int i = 0; i < 12; i++) {
        var call =
            ProtocolCallLog.start(
                "HTTP",
                "http://localhost/api",
                Map.of("body", Map.of("content", "x".repeat(2000), "password", "RawLimitSecret")));
        call.complete(Map.of("body", "not-json RawPlainSecret"), null);
      }
      Assert.assertTrue(Files.exists(Path.of(file + ".1")));
      try (var files = Files.list(file.getParent())) {
        for (Path log : files.toList()) {
          String text = Files.readString(log);
          Assert.assertFalse(text.contains("RawLimitSecret"));
          Assert.assertFalse(text.contains("RawPlainSecret"));
          for (String row : Files.readAllLines(log)) JSON.readTree(row);
        }
      }
    }
  }

  public void failureLogsTypeButNeverEchoExceptionSecrets() throws Exception {
    Path file = Files.createTempDirectory("call-error").resolve("calls.jsonl");
    try (var settings = new Settings(file, true)) {
      ProtocolCallLog.start("DUBBO", "dubbo://localhost:20880", Map.of("method", "create"))
          .complete(
              Map.of(),
              new IllegalStateException(
                  "RawExceptionSecret", new java.io.IOException("RawCauseSecret")));
    }
    String output = Files.readString(file);
    Assert.assertTrue(output.contains("ERROR"));
    Assert.assertTrue(output.contains("java.io.IOException"));
    Assert.assertFalse(output.contains("RawExceptionSecret"));
    Assert.assertFalse(output.contains("RawCauseSecret"));
  }

  public void concurrentLinesRemainCompleteAndIoFailureDoesNotBreakCalls() throws Exception {
    Path directory = Files.createTempDirectory("call-parallel");
    Path file = directory.resolve("calls.jsonl");
    try (var settings = new Settings(file, true)) {
      var pool = Executors.newFixedThreadPool(4);
      try {
        List<java.util.concurrent.Future<?>> jobs = new ArrayList<>();
        for (int i = 0; i < 24; i++)
          jobs.add(
              pool.submit(
                  () ->
                      ProtocolCallLog.start(
                              "HTTP", "http://localhost/api", Map.of("method", "POST"))
                          .complete(Map.of("status", 200), null)));
        for (var job : jobs) job.get();
      } finally {
        pool.shutdownNow();
      }
      Assert.assertEquals(Files.readAllLines(file).size(), 48);
      for (String row : Files.readAllLines(file)) JSON.readTree(row);
      settings.set("testkit.log.file", directory.toString());
      ProtocolCallLog.start("HTTP", "http://localhost/api", Map.of()).complete(Map.of(), null);
    }
  }
}
