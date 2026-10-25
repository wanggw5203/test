package io.testkit.basetest.runtime;

/** Implement on a test instance to avoid class-parallel global install/reset races. */
public interface RuntimeProvider {
  AutomationRuntime automationRuntime();
}
