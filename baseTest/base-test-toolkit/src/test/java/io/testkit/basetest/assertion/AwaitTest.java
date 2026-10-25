package io.testkit.basetest.assertion;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AwaitTest {
  @Test
  public void pollsUntilReadyAndDoesNotSwallowFailures() {
    AtomicInteger calls = new AtomicInteger();
    Assert.assertEquals(
        Await.until(
            "ready",
            Duration.ofSeconds(2),
            Duration.ofMillis(1),
            calls::incrementAndGet,
            n -> n == 3),
        Integer.valueOf(3));
    Assert.expectThrows(
        AssertionError.class,
        () ->
            Await.until("never", Duration.ofMillis(5), Duration.ofMillis(1), () -> false, x -> x));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            Await.until(
                "error",
                Duration.ofSeconds(1),
                Duration.ofMillis(1),
                () -> {
                  throw new IllegalArgumentException();
                },
                x -> true));
    Thread.currentThread().interrupt();
    try {
      Assert.expectThrows(
          IllegalStateException.class,
          () ->
              Await.until(
                  "interrupt", Duration.ofSeconds(1), Duration.ofMillis(1), () -> true, x -> x));
      Assert.assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }
}
