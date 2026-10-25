package io.testkit.basetest.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Append-only, synchronized JSONL. One instance per file; no retained writer requiring close. */
public final class JsonLinesResultPublisher implements ResultPublisher, InvocationLogSink {
  private final Path file;
  private final ObjectMapper json = new ObjectMapper();

  public JsonLinesResultPublisher(Path file) {
    this.file = file.toAbsolutePath();
    try {
      Files.createDirectories(this.file.getParent());
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create report directory", e);
    }
  }

  @Override
  public void publish(TestResultRecord result) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("type", "test");
    row.put("runId", result.runId());
    row.put("caseId", result.caseId());
    row.put("name", result.name());
    row.put("status", result.status().name());
    row.put("startedAt", result.startedAt() == null ? null : result.startedAt().toString());
    row.put("durationMillis", result.durationMillis());
    Object phase = result.metadata().get("phase");
    if ("configuration".equals(phase) || "test".equals(phase)) row.put("phase", phase);
    for (String key :
        java.util.List.of(
            "logicalCaseId", "owner", "team", "service", "level", "environment", "workerId")) {
      Object value = result.metadata().get(key);
      if (value instanceof String safe && safe.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))
        row.put(key, safe);
    }
    for (String key : java.util.List.of("shardIndex", "shardCount")) {
      Object value = result.metadata().get(key);
      if (value instanceof Integer count && count >= 0 && count <= 256) row.put(key, count);
    }
    // Throwable messages and arbitrary metadata may contain secrets. Detailed failures stay in
    // TestNG reports.
    row.put("hasError", result.error() != null);
    append(row);
  }

  @Override
  public void complete(TestRunSummary summary) {
    append(
        Map.of(
            "type",
            "summary",
            "runId",
            summary.runId(),
            "startedAt",
            summary.startedAt().toString(),
            "endedAt",
            summary.endedAt().toString(),
            "passed",
            summary.passed(),
            "failed",
            summary.failed(),
            "skipped",
            summary.skipped()));
  }

  @Override
  public void accept(InvocationRecord record) {
    // Whitelist structural metrics only, including for custom clients passing sensitive records.
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("type", "invocation");
    row.put("time", record.time().toString());
    row.put("durationMillis", record.durationMillis());
    row.put("hasError", record.error() != null);
    row.put("runId", record.runId());
    row.put("caseId", record.caseId());
    if (record.response() instanceof Integer status) row.put("status", status);
    append(row);
  }

  private synchronized void append(Map<String, Object> row) {
    try {
      Files.writeString(
          file,
          json.writeValueAsString(row) + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot append test report", e);
    }
  }
}
