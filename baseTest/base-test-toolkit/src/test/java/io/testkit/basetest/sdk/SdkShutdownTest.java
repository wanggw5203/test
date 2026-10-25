package io.testkit.basetest.sdk;

import io.testkit.basetest.assertion.Await;
import io.testkit.basetest.runtime.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SdkShutdownTest {
  @Test
  public void closeCancelsRunningAndQueuedCallsAndLogsRejections() throws Exception {
    var logs = new CopyOnWriteArrayList<InvocationRecord>();
    var runtime = new AutomationRuntime(null, null, null, null, logs::add, null, null, null);
    var sdk = new SdkInvoker(1, 2);
    var callers = Executors.newFixedThreadPool(2);
    CountDownLatch running = new CountDownLatch(1),
        block = new CountDownLatch(1),
        submitted = new CountDownLatch(1);
    AtomicBoolean queuedRan = new AtomicBoolean();
    AtomicReference<Thread> queuedCaller = new AtomicReference<>();
    try {
      var active =
          callers.submit(
              () -> {
                try (var scope = RuntimeRegistry.use(runtime)) {
                  return sdk.call(
                      "running",
                      Duration.ofSeconds(30),
                      () -> {
                        running.countDown();
                        block.await();
                        return "done";
                      });
                }
              });
      Assert.assertTrue(running.await(2, TimeUnit.SECONDS));
      var queued =
          callers.submit(
              () -> {
                try (var scope = RuntimeRegistry.use(runtime)) {
                  submitted.countDown();
                  queuedCaller.set(Thread.currentThread());
                  return sdk.call(
                      "queued",
                      Duration.ofSeconds(30),
                      () -> {
                        queuedRan.set(true);
                        return "done";
                      });
                }
              });
      Assert.assertTrue(submitted.await(2, TimeUnit.SECONDS));
      Await.until(
          "queued caller waiting on future",
          Duration.ofSeconds(2),
          Duration.ofMillis(1),
          queuedCaller::get,
          thread -> thread != null && thread.getState() == Thread.State.TIMED_WAITING);
      sdk.close();
      sdk.close();
      for (var future : List.of(active, queued)) {
        var failure =
            Assert.expectThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        Assert.assertTrue(
            failure.getCause() instanceof IllegalStateException
                || failure.getCause() instanceof RejectedExecutionException);
      }
      Assert.assertFalse(queuedRan.get());
      try (var scope = RuntimeRegistry.use(runtime)) {
        Assert.expectThrows(
            RejectedExecutionException.class,
            () -> sdk.call("closed", Duration.ofSeconds(1), () -> "no"));
      }
      Assert.assertEquals(logs.size(), 3);
      Assert.assertTrue(logs.stream().allMatch(l -> l.error() != null));
    } finally {
      block.countDown();
      sdk.close();
      callers.shutdownNow();
    }
  }
}
