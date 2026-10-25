package io.testkit.adapters.xxljob;

import com.fasterxml.jackson.databind.JsonNode;
import io.testkit.basetest.assertion.Await;
import io.testkit.basetest.http.*;
import io.testkit.basetest.job.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/**
 * Admin-session adapter for dedicated test jobs. Trigger acceptance and execution completion remain
 * separate.
 */
public final class XxlJobService implements JobService, AutoCloseable {
  private final ApiClient api;
  private final XxlJobDialect dialect;
  private final String environment;
  private final int group;
  private final Map<String, Integer> jobs;
  private final Duration logTimeout;
  private final Map<String, Integer> tracked = new HashMap<>();
  private final Map<String, Instant> triggered = new HashMap<>();
  private final Set<Integer> uncertainJobs = new HashSet<>();
  private String cookie;
  private boolean closed;

  public XxlJobService(
      String adminUrl,
      String environment,
      int group,
      Map<String, Integer> jobs,
      XxlJobDialect dialect,
      Duration timeout) {
    if (group <= 0 || jobs.values().stream().anyMatch(i -> i <= 0))
      throw new IllegalArgumentException("Positive XXL-JOB ids required");
    this.api =
        new ApiClient(adminUrl.endsWith("/") ? adminUrl : adminUrl + "/", timeout, record -> {});
    this.environment = Objects.requireNonNull(environment);
    this.group = group;
    this.jobs = Map.copyOf(jobs);
    this.dialect = Objects.requireNonNull(dialect);
    this.logTimeout = timeout;
  }

  public synchronized void login(String username, String password) {
    if (closed) throw new IllegalStateException("XXL-JOB adapter closed");
    cookie = null;
    ApiResponse response = post(dialect.loginPath(), dialect.login(username, password));
    if (!dialect.accepted(response.json()))
      throw new IllegalStateException("XXL-JOB login rejected");
    cookie =
        response.headers().entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase("set-cookie"))
            .flatMap(e -> e.getValue().stream())
            .map(v -> v.split(";", 2)[0])
            .filter(v -> v.startsWith(dialect.cookieName() + "="))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("XXL-JOB login cookie missing"));
  }

  @Override
  public synchronized JobExecution trigger(JobRequest request) {
    requireSession();
    if (!environment.equals(request.environment()))
      throw new IllegalArgumentException("XXL-JOB environment mismatch");
    Integer job = jobs.get(request.handler());
    if (job == null)
      throw new IllegalArgumentException("XXL-JOB handler is not mapped to an approved test job");
    if (uncertainJobs.contains(job))
      throw new XxlJobSubmissionException(
          XxlJobSubmissionException.Outcome.OUTCOME_UNKNOWN, job, null);
    if (tracked.size() >= 1000)
      throw new IllegalStateException("XXL-JOB tracking capacity reached; create a new adapter");
    long before = 0;
    for (JsonNode row : rows(job)) before = Math.max(before, row.path("id").asLong());
    String parameter;
    try {
      parameter =
          io.testkit.basetest.config.ConfigLoader.jsonMapper()
              .writeValueAsString(request.parameters());
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Cannot serialize job parameters", e);
    }
    Instant time = Instant.now();
    JsonNode triggerResponse;
    try {
      triggerResponse = post(dialect.triggerPath(), dialect.trigger(job, parameter)).json();
    } catch (RuntimeException | AssertionError failure) {
      throw unknown(job, failure);
    }
    boolean accepted;
    boolean rejected;
    try {
      accepted = dialect.accepted(triggerResponse);
      rejected = !accepted && dialect.rejected(triggerResponse);
    } catch (RuntimeException | AssertionError failure) {
      throw unknown(job, failure);
    }
    if (!accepted) {
      if (rejected)
        throw new XxlJobSubmissionException(XxlJobSubmissionException.Outcome.REJECTED, job, null);
      throw unknown(job, new IllegalStateException("Unsupported XXL-JOB trigger acknowledgement"));
    }
    long baseline = before;
    // Never trigger again if log discovery times out: the original execution may already be
    // running.
    JsonNode log;
    try {
      log =
          Await.until(
              "XXL-JOB execution log after accepted trigger",
              logTimeout,
              Duration.ofMillis(100),
              () -> {
                List<JsonNode> fresh = new ArrayList<>();
                for (JsonNode row : rows(job))
                  if (row.path("id").asLong() > baseline) fresh.add(row);
                if (fresh.size() > 1)
                  throw new IllegalStateException(
                      "Ambiguous XXL-JOB executions; use an isolated test job");
                return fresh.isEmpty() ? null : fresh.get(0);
              },
              Objects::nonNull);
    } catch (RuntimeException | AssertionError failure) {
      throw unknown(job, failure);
    }
    String id = Long.toString(log.path("id").asLong());
    tracked.put(id, job);
    triggered.put(id, time);
    return execution(log, time);
  }

  @Override
  public synchronized Optional<JobExecution> query(String id) {
    requireSession();
    Integer job = tracked.get(id);
    if (job == null) return Optional.empty();
    for (JsonNode row : rows(job))
      if (Long.toString(row.path("id").asLong()).equals(id))
        return Optional.of(execution(row, triggered.get(id)));
    return Optional.empty();
  }

  private JobExecution execution(JsonNode row, Instant time) {
    int trigger = row.path("triggerCode").asInt();
    int handle = row.path("handleCode").asInt();
    JobExecution.State state =
        trigger > 0 && trigger != 200
            ? JobExecution.State.FAILED
            : trigger == 200 && handle == 200
                ? JobExecution.State.SUCCEEDED
                : handle > 0
                    ? JobExecution.State.FAILED
                    : trigger == 200 ? JobExecution.State.RUNNING : JobExecution.State.QUEUED;
    return new JobExecution(Long.toString(row.path("id").asLong()), state, time, null);
  }

  private JsonNode rows(int job) {
    return dialect.rows(post(dialect.logsPath(), dialect.logs(group, job)).json());
  }

  private ApiResponse post(String path, Map<String, String> fields) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/x-www-form-urlencoded");
    if (cookie != null) headers.put("Cookie", cookie);
    String body =
        fields.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(java.util.stream.Collectors.joining("&"));
    return api.request("POST", path, headers, body).assertStatus(200);
  }

  private void requireSession() {
    if (closed || cookie == null) throw new IllegalStateException("XXL-JOB login required");
  }

  private XxlJobSubmissionException unknown(int job, Throwable cause) {
    uncertainJobs.add(job);
    return new XxlJobSubmissionException(
        XxlJobSubmissionException.Outcome.OUTCOME_UNKNOWN, job, cause);
  }

  @Override
  public synchronized void close() {
    closed = true;
    cookie = null;
    tracked.clear();
    triggered.clear();
    uncertainJobs.clear();
  }
}
