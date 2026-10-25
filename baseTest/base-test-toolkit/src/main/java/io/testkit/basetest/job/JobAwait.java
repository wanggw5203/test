package io.testkit.basetest.job;

import io.testkit.basetest.assertion.Await;
import java.time.Duration;
import java.util.Objects;

/** Waits for a known execution; business failure/cancellation stops polling immediately. */
public final class JobAwait {
  private JobAwait() {}

  public static JobExecution succeeded(
      JobService jobs, String id, Duration timeout, Duration interval) {
    Objects.requireNonNull(jobs);
    Objects.requireNonNull(id);
    return Await.until(
        "job completion",
        timeout,
        interval,
        () -> jobs.query(id).orElse(null),
        execution -> {
          if (execution == null) return false;
          if (!id.equals(execution.id()))
            throw new IllegalStateException("Job query returned a different execution");
          if (execution.state() == JobExecution.State.FAILED
              || execution.state() == JobExecution.State.CANCELLED)
            throw new AssertionError("Job ended with state: " + execution.state());
          if (execution.state() == JobExecution.State.UNKNOWN)
            throw new AssertionError(
                "Job outcome unknown; reconcile the existing execution before submitting again");
          return execution.state() == JobExecution.State.SUCCEEDED;
        });
  }
}
