package io.testkit.basetest.job;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Local one-shot scheduler and job-state oracle; not a distributed cron platform. */
public final class LocalJobService implements JobService, AutoCloseable {
  private final String environment;
  private final ScheduledExecutorService scheduler;
  private final Map<String, Consumer<JobRequest>> handlers;
  private final Map<String, JobExecution> executions = new ConcurrentHashMap<>();
  private final int maxExecutions;
  private boolean closed;

  public LocalJobService(
      String environment,
      Map<String, Consumer<JobRequest>> handlers,
      int threads,
      int maxExecutions) {
    if (threads <= 0 || maxExecutions <= 0)
      throw new IllegalArgumentException("Positive scheduler limits required");
    this.environment = Objects.requireNonNull(environment);
    this.handlers = Map.copyOf(handlers);
    this.maxExecutions = maxExecutions;
    scheduler =
        Executors.newScheduledThreadPool(
            threads,
            runnable -> {
              Thread t = new Thread(runnable, "testkit-job");
              t.setDaemon(true);
              return t;
            });
  }

  @Override
  public JobExecution trigger(JobRequest request) {
    return schedule(request, Duration.ZERO);
  }

  public synchronized JobExecution schedule(JobRequest request, Duration delay) {
    if (closed) throw new IllegalStateException("Local job service closed");
    if (delay.isNegative()) throw new IllegalArgumentException("Negative job delay");
    if (!environment.equals(request.environment()))
      throw new IllegalArgumentException("Job environment mismatch");
    Consumer<JobRequest> handler = handlers.get(request.handler());
    if (handler == null) throw new IllegalArgumentException("Unknown job handler");
    if (executions.size() >= maxExecutions)
      throw new RejectedExecutionException("Job history capacity reached");
    String id = UUID.randomUUID().toString();
    Instant now = Instant.now();
    JobExecution queued = new JobExecution(id, JobExecution.State.QUEUED, now, null);
    executions.put(id, queued);
    try {
      scheduler.schedule(
          () -> {
            synchronized (LocalJobService.this) {
              if (closed) return;
              executions.put(id, new JobExecution(id, JobExecution.State.RUNNING, now, null));
            }
            JobExecution completed;
            try {
              handler.accept(request);
              completed = new JobExecution(id, JobExecution.State.SUCCEEDED, now, null);
            } catch (Throwable failure) {
              completed =
                  new JobExecution(
                      id, JobExecution.State.FAILED, now, failure.getClass().getName());
            }
            synchronized (LocalJobService.this) {
              // Interrupting a worker cannot prove that its business side effects stopped.
              if (!closed) executions.put(id, completed);
            }
          },
          delay.toNanos(),
          TimeUnit.NANOSECONDS);
    } catch (RuntimeException e) {
      executions.remove(id);
      throw e;
    }
    return queued;
  }

  @Override
  public Optional<JobExecution> query(String id) {
    return Optional.ofNullable(executions.get(id));
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    scheduler.shutdownNow();
    executions.replaceAll(
        (id, execution) ->
            execution.state() == JobExecution.State.QUEUED
                ? new JobExecution(
                    id, JobExecution.State.CANCELLED, execution.triggeredAt(), "Scheduler closed")
                : execution.state() == JobExecution.State.RUNNING
                    ? new JobExecution(
                        id,
                        JobExecution.State.UNKNOWN,
                        execution.triggeredAt(),
                        "Scheduler closed; running handler outcome unknown")
                    : execution);
  }
}
