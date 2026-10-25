package io.testkit.basetest.enterprise;

/**
 * Immutable execution identity included in plans, reports and fixture names.
 *
 * <p>Identifiers contain 1-64 ASCII letters, digits, dots, underscores or hyphens, starting with a
 * letter or digit. They must not contain credentials or user data. All workers in one CI run share
 * a run ID and use distinct worker IDs.
 *
 * @param runId CI execution identity shared by cooperating workers
 * @param workerId unique worker identity within the run
 * @param environment resolved client environment, such as local, dev or qa
 * @param team accountable team identifier
 */
public record RunMetadata(String runId, String workerId, String environment, String team) {
  public RunMetadata {
    token(runId, "runId");
    token(workerId, "workerId");
    token(environment, "environment");
    token(team, "team");
  }

  static String token(String value, String field) {
    if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
      throw new IllegalArgumentException("Expected safe identifier for " + field);
    }
    return value;
  }
}
