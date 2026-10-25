package io.testkit.basetest.enterprise;

import io.testkit.basetest.runtime.RuntimeProvider;

/** Explicit TestNG integration contract for enterprise admission and runtime isolation. */
public interface EnterpriseRuntimeProvider extends RuntimeProvider {
  /** Returns a stable immutable execution configuration for this test instance. */
  EnterpriseExecution enterpriseExecution();
}
