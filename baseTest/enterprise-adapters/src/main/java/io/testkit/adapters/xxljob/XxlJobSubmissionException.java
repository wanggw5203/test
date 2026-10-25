package io.testkit.adapters.xxljob;

/**
 * Identifies a rejected submission separately from a possibly running job whose execution cannot be
 * identified.
 */
public final class XxlJobSubmissionException extends IllegalStateException {
  public enum Outcome {
    REJECTED,
    OUTCOME_UNKNOWN
  }

  private final Outcome outcome;
  private final int jobId;

  public XxlJobSubmissionException(Outcome outcome, int jobId, Throwable cause) {
    super(
        outcome == Outcome.OUTCOME_UNKNOWN
            ? "XXL-JOB submission outcome unknown; reconcile the dedicated job before submitting again"
            : "XXL-JOB submission rejected",
        cause);
    this.outcome = java.util.Objects.requireNonNull(outcome);
    this.jobId = jobId;
  }

  public Outcome outcome() {
    return outcome;
  }

  public int jobId() {
    return jobId;
  }
}
