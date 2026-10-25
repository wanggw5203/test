package io.testkit.basetest.runtime;

import java.time.Instant;
import java.util.Map;

public record InvocationRecord(
    Instant time,
    String protocol,
    String target,
    Map<String, Object> request,
    Object response,
    long durationMillis,
    String error,
    String runId,
    String caseId) {
  public InvocationRecord(
      Instant time,
      String protocol,
      String target,
      Map<String, Object> request,
      Object response,
      long durationMillis,
      String error) {
    this(
        time,
        protocol,
        target,
        request,
        response,
        durationMillis,
        error,
        TraceContext.current().map(TraceContext.Trace::runId).orElse(null),
        TraceContext.current().map(TraceContext.Trace::caseId).orElse(null));
  }

  public InvocationRecord {
    request = Map.copyOf(request == null ? Map.of() : request);
  }

  @Override
  public String toString() {
    return "InvocationRecord[protocol="
        + protocol
        + ", durationMillis="
        + durationMillis
        + ", payload=<omitted>]";
  }
}
