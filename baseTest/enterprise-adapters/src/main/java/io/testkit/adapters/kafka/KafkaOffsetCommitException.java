package io.testkit.adapters.kafka;

/**
 * The handler completed, but its offset commit was not confirmed. Business side effects may already
 * exist.
 */
public final class KafkaOffsetCommitException extends IllegalStateException {
  private final long nextOffset;

  public KafkaOffsetCommitException(long nextOffset, Throwable cause) {
    super(
        "Kafka handler completed but offset commit was not confirmed; reconcile before reprocessing",
        cause);
    this.nextOffset = nextOffset;
  }

  public long nextOffset() {
    return nextOffset;
  }
}
