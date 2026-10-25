package io.testkit.basetest.assertion;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Explicit polling for eventual consistency; supplier exceptions fail immediately. */
public final class Await {
  private Await() {}

  public static <T> T until(
      String description,
      Duration timeout,
      Duration interval,
      Supplier<T> supplier,
      Predicate<T> ready) {
    Objects.requireNonNull(supplier);
    Objects.requireNonNull(ready);
    if (timeout.isNegative() || timeout.isZero() || interval.isNegative() || interval.isZero())
      throw new IllegalArgumentException("Timeout and interval must be positive");
    long start = System.nanoTime();
    long budget = timeout.toNanos();
    do {
      if (Thread.currentThread().isInterrupted())
        throw new IllegalStateException("Polling interrupted");
      T value = supplier.get();
      if (ready.test(value)) return value;
      long remaining = budget - (System.nanoTime() - start);
      if (remaining <= 0) break;
      try {
        java.util.concurrent.TimeUnit.NANOSECONDS.sleep(Math.min(interval.toNanos(), remaining));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Polling interrupted", e);
      }
    } while (System.nanoTime() - start < budget);
    throw new AssertionError("Timed out waiting for: " + description);
  }
}
