package io.testkit.adapters.kafka;

/**
 * Transport confirmation failure. An unknown outcome must be reconciled before any
 * application-level resend.
 */
public final class KafkaDeliveryException extends IllegalStateException {
  public enum Outcome {
    ACKNOWLEDGEMENT_FAILED,
    OUTCOME_UNKNOWN
  }

  private final Outcome outcome;

  public KafkaDeliveryException(Outcome outcome, Throwable cause) {
    super(
        outcome == Outcome.OUTCOME_UNKNOWN
            ? "Kafka delivery outcome unknown; reconcile correlation evidence before resending"
            : "Kafka delivery acknowledgement failed",
        cause);
    this.outcome = java.util.Objects.requireNonNull(outcome);
  }

  public Outcome outcome() {
    return outcome;
  }
}
