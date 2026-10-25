package io.testkit.basetest.job;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JobAwaitTest {
  private JobService service(java.util.function.Supplier<Optional<JobExecution>> query) {
    return new JobService() {
      public JobExecution trigger(JobRequest request) {
        throw new UnsupportedOperationException();
      }

      public Optional<JobExecution> query(String id) {
        return query.get();
      }
    };
  }

  @Test
  public void waitsThroughMissingAndRunningThenReturnsSuccess() {
    AtomicInteger count = new AtomicInteger();
    var jobs =
        service(
            () -> {
              int step = count.incrementAndGet();
              return step == 1
                  ? Optional.empty()
                  : Optional.of(
                      new JobExecution(
                          "id",
                          step == 2 ? JobExecution.State.RUNNING : JobExecution.State.SUCCEEDED,
                          Instant.now(),
                          null));
            });
    Assert.assertEquals(
        JobAwait.succeeded(jobs, "id", Duration.ofSeconds(1), Duration.ofMillis(1)).state(),
        JobExecution.State.SUCCEEDED);
    Assert.assertEquals(count.get(), 3);
  }

  @Test
  public void terminalFailureIsImmediateAndUnknownCannotPass() {
    for (var state : List.of(JobExecution.State.FAILED, JobExecution.State.CANCELLED)) {
      AtomicInteger count = new AtomicInteger();
      var jobs =
          service(
              () -> {
                count.incrementAndGet();
                return Optional.of(new JobExecution("id", state, Instant.now(), "secret"));
              });
      var failure =
          Assert.expectThrows(
              AssertionError.class,
              () -> JobAwait.succeeded(jobs, "id", Duration.ofSeconds(1), Duration.ofMillis(1)));
      Assert.assertEquals(count.get(), 1);
      Assert.assertFalse(failure.getMessage().contains("secret"));
    }
    AtomicInteger unknownQueries = new AtomicInteger();
    var unknown =
        service(
            () -> {
              unknownQueries.incrementAndGet();
              return Optional.of(
                  new JobExecution("id", JobExecution.State.UNKNOWN, Instant.now(), null));
            });
    Assert.expectThrows(
        AssertionError.class,
        () -> JobAwait.succeeded(unknown, "id", Duration.ofMillis(5), Duration.ofMillis(1)));
    Assert.assertEquals(unknownQueries.get(), 1);
    var wrong =
        service(
            () ->
                Optional.of(
                    new JobExecution("other", JobExecution.State.SUCCEEDED, Instant.now(), null)));
    Assert.expectThrows(
        IllegalStateException.class,
        () -> JobAwait.succeeded(wrong, "id", Duration.ofSeconds(1), Duration.ofMillis(1)));
  }
}
