package io.testkit.basetest.job;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LocalJobServiceTest {
  @Test
  public void closeDistinguishesCancelledQueueFromUnknownRunningWork() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch interrupted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch returned = new CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<Thread> worker =
        new java.util.concurrent.atomic.AtomicReference<>();
    var jobs =
        new LocalJobService(
            "qa",
            Map.of(
                "settlement",
                request -> {
                  worker.set(Thread.currentThread());
                  started.countDown();
                  boolean done = false;
                  while (!done) {
                    try {
                      release.await();
                      done = true;
                    } catch (InterruptedException ignored) {
                      interrupted.countDown();
                    }
                  }
                  returned.countDown();
                }),
            1,
            10);
    var request = new JobRequest("orders", "settlement", "qa", null, Map.of());
    try {
      var running = jobs.trigger(request);
      Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
      var queued = jobs.schedule(request, Duration.ofHours(1));
      jobs.close();
      Assert.assertTrue(interrupted.await(2, TimeUnit.SECONDS));
      Assert.assertEquals(
          jobs.query(running.id()).orElseThrow().state(), JobExecution.State.UNKNOWN);
      Assert.assertEquals(
          jobs.query(queued.id()).orElseThrow().state(), JobExecution.State.CANCELLED);
      Assert.expectThrows(IllegalStateException.class, () -> jobs.trigger(request));
      release.countDown();
      Assert.assertTrue(returned.await(2, TimeUnit.SECONDS));
      worker.get().join(2000);
      Assert.assertFalse(worker.get().isAlive());
      Assert.assertEquals(
          jobs.query(running.id()).orElseThrow().state(),
          JobExecution.State.UNKNOWN,
          "A handler returning after shutdown cannot retroactively certify an uncertain execution");
      jobs.close();
    } finally {
      release.countDown();
      jobs.close();
    }
  }
}
