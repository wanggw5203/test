package io.testkit.basetest.message;

import java.time.Duration;
import java.util.function.Predicate;

/** Caller must open/subscribe the probe before publishing the correlated request. */
public interface MessageProbe extends AutoCloseable {
  TestMessage await(Predicate<TestMessage> matches, Duration timeout);

  @Override
  void close();
}
