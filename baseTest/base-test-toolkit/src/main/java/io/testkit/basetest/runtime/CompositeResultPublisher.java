package io.testkit.basetest.runtime;

import java.util.List;
import java.util.function.Consumer;

/** Fan-out attempts every publisher even when one fails. */
public final class CompositeResultPublisher implements ResultPublisher {
  private final List<ResultPublisher> publishers;

  public CompositeResultPublisher(ResultPublisher... publishers) {
    this.publishers = List.of(publishers);
  }

  @Override
  public void publish(TestResultRecord result) {
    each(p -> p.publish(result));
  }

  @Override
  public void complete(TestRunSummary summary) {
    each(p -> p.complete(summary));
  }

  private void each(Consumer<ResultPublisher> action) {
    RuntimeException combined = new IllegalStateException("Result publisher failed");
    for (ResultPublisher publisher : publishers) {
      try {
        action.accept(publisher);
      } catch (RuntimeException | Error e) {
        combined.addSuppressed(e);
      }
    }
    if (combined.getSuppressed().length != 0) throw combined;
  }
}
