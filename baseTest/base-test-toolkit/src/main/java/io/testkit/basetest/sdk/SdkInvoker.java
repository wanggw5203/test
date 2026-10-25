package io.testkit.basetest.sdk;

import io.testkit.basetest.enterprise.EnterpriseContext;
import io.testkit.basetest.identity.*;
import io.testkit.basetest.runtime.*;
import java.time.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

/** Bounded executor for typed SDK calls. Contexts explicitly propagated; no implicit retries. */
public final class SdkInvoker implements AutoCloseable {
  private final ThreadPoolExecutor executor;
  private final Set<Future<?>> pending = new HashSet<>();
  private boolean closed;

  public SdkInvoker(int concurrency, int queueCapacity) {
    if (concurrency <= 0 || queueCapacity <= 0)
      throw new IllegalArgumentException("Positive worker/queue limits required");
    executor =
        new ThreadPoolExecutor(
            concurrency,
            concurrency,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            runnable -> {
              Thread thread = new Thread(runnable, "testkit-sdk");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  public <T> T call(String operation, Duration timeout, Callable<T> action) {
    Objects.requireNonNull(operation);
    Objects.requireNonNull(action);
    if (timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("Positive timeout required");
    long timeoutNanos = timeout.toNanos();
    AutomationRuntime runtime = RuntimeRegistry.current();
    UserIdentity identity = IdentityContext.current().orElse(null);
    TraceContext.Trace trace = TraceContext.current().orElse(null);
    EnterpriseContext.Snapshot enterprise = EnterpriseContext.current().orElse(null);
    Instant started = Instant.now();
    long nanos = System.nanoTime();
    FutureTask<T> future =
        new FutureTask<>(
            () -> {
              try (var scope = RuntimeRegistry.use(runtime);
                  var user = identity == null ? null : IdentityContext.use(identity);
                  var correlation = trace == null ? null : TraceContext.use(trace);
                  var teamContext = enterprise == null ? null : EnterpriseContext.use(enterprise)) {
                return action.call();
              }
            });
    RuntimeException failure = null;
    try {
      synchronized (pending) {
        if (closed) throw new RejectedExecutionException("SDK invoker is closed");
        pending.add(future);
        executor.execute(future);
      }
      return future.get(timeoutNanos, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      failure = new IllegalStateException("SDK interrupted", e);
      throw failure;
    } catch (TimeoutException e) {
      future.cancel(true);
      failure = new IllegalStateException("SDK timeout", e);
      throw failure;
    } catch (ExecutionException e) {
      failure = new IllegalStateException("SDK call failed", e.getCause());
      throw failure;
    } catch (CancellationException e) {
      failure = new IllegalStateException("SDK call cancelled", e);
      throw failure;
    } catch (RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      synchronized (pending) {
        pending.remove(future);
      }
      executor.remove(future);
      try {
        runtime
            .invocationLogs()
            .accept(
                new InvocationRecord(
                    started,
                    "sdk",
                    operation,
                    Map.of(),
                    null,
                    (System.nanoTime() - nanos) / 1_000_000,
                    failure == null ? null : failure.getClass().getSimpleName()));
      } catch (RuntimeException | Error logging) {
        if (failure == null) throw logging;
        failure.addSuppressed(logging);
      }
    }
  }

  @Override
  public void close() {
    synchronized (pending) {
      if (closed) return;
      closed = true;
      pending.forEach(future -> future.cancel(true));
      executor.shutdownNow();
    }
  }
}
