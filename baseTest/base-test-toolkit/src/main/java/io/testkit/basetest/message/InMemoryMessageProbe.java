package io.testkit.basetest.message;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Bounded local consumer oracle; offer fails on overflow instead of silently dropping evidence. */
public final class InMemoryMessageProbe implements MessageProbe {
  private final BlockingQueue<TestMessage> messages;
  private volatile boolean closed;

  public InMemoryMessageProbe(int capacity) {
    messages = new ArrayBlockingQueue<>(capacity);
  }

  public void publish(TestMessage message) {
    if (closed) throw new IllegalStateException("Probe closed");
    if (!messages.offer(message)) throw new RejectedExecutionException("Message buffer full");
  }

  @Override
  public TestMessage await(Predicate<TestMessage> matches, Duration timeout) {
    if (timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("Positive timeout required");
    long start = System.nanoTime();
    do {
      if (closed) throw new IllegalStateException("Probe closed");
      long remaining = timeout.toNanos() - (System.nanoTime() - start);
      if (remaining <= 0) break;
      try {
        TestMessage message =
            messages.poll(
                Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS);
        if (message != null && matches.test(message)) return message;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Message wait interrupted", e);
      }
    } while (true);
    throw new AssertionError("No correlated message before timeout");
  }

  @Override
  public void close() {
    closed = true;
    messages.clear();
  }
}
