package io.testkit.basetest.enterprise;

/** Classification used to select an independently executable test plan. */
public enum CaseLevel {
  /** A small set of critical service and business-path checks. */
  SMOKE,
  /** Broader correctness and compatibility coverage. */
  REGRESSION
}
