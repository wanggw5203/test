package io.testkit.basetest.config;

import io.testkit.basetest.http.ApiClient;
import io.testkit.basetest.runtime.AutomationRuntime;
import io.testkit.basetest.runtime.RuntimeRegistry;
import io.testkit.basetest.sdk.SdkInvoker;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

/** Per-suite clients. Only requested capabilities are initialized; owner closes SDK resources. */
public final class TestClients implements AutoCloseable {
  private final EnvironmentConfig config;
  private final AutomationRuntime runtime;
  private ApiClient http;
  private SdkInvoker sdk;
  private boolean closed;

  public TestClients(EnvironmentConfig config, AutomationRuntime runtime) {
    this.config = Objects.requireNonNull(config);
    this.runtime = Objects.requireNonNull(runtime);
  }

  public synchronized ApiClient http() {
    checkOpen();
    if (http == null)
      http =
          new ApiClient(
              config.require("http.base.url"),
              Duration.ofMillis(config.positiveInt("http.timeout.ms")),
              runtime.invocationLogs());
    return http;
  }

  public <T> T callSdk(String operation, Callable<T> action) {
    SdkInvoker invoker;
    Duration timeout;
    synchronized (this) {
      checkOpen();
      timeout = Duration.ofMillis(config.positiveInt("sdk.timeout.ms"));
      if (sdk == null)
        sdk =
            new SdkInvoker(
                config.positiveInt("sdk.concurrency"), config.positiveInt("sdk.queue.capacity"));
      invoker = sdk;
    }
    try (var scope = RuntimeRegistry.use(runtime)) {
      return invoker.call(operation, timeout, action);
    }
  }

  private void checkOpen() {
    if (closed) throw new IllegalStateException("Test clients are closed");
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    if (sdk != null) sdk.close();
  }
}
