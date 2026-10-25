package io.testkit.basetest.lease;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.h2.jdbcx.JdbcDataSource;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JdbcResourceLeaseTest {
  private JdbcDataSource database() throws Exception {
    var source = new JdbcDataSource();
    source.setURL("jdbc:h2:mem:lease-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    try (var connection = source.getConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE testkit_resource_lease(resource_key VARCHAR(160) PRIMARY KEY,owner_token VARCHAR(64),expires_at TIMESTAMP NOT NULL,fence BIGINT NOT NULL)");
    }
    return source;
  }

  @Test
  public void separateProvidersCompeteForOneOwnerAndReleaseAllowsNextFence() throws Exception {
    var source = database();
    var pool = Executors.newFixedThreadPool(16);
    var barrier = new CyclicBarrier(16);
    List<Future<Optional<ResourceLease>>> futures = new ArrayList<>();
    List<ResourceLease> winners = new ArrayList<>();
    try {
      for (int i = 0; i < 16; i++)
        futures.add(
            pool.submit(
                () -> {
                  barrier.await();
                  return new JdbcResourceLeaseProvider(source, "testkit_resource_lease")
                      .tryAcquire("qa:account:alice", Duration.ofSeconds(30));
                }));
      for (var future : futures) future.get(10, TimeUnit.SECONDS).ifPresent(winners::add);
      Assert.assertEquals(winners.size(), 1);
      var lease = winners.get(0);
      Assert.assertTrue(lease.valid());
      Assert.assertTrue(lease.renew(Duration.ofSeconds(30)));
      Assert.assertEquals(lease.fencingToken(), 1L);
      lease.close();
      lease.close();
      Assert.assertFalse(lease.valid());
      try (var next =
          new JdbcResourceLeaseProvider(source, "testkit_resource_lease")
              .tryAcquire("qa:account:alice", Duration.ofSeconds(30))
              .orElseThrow()) {
        Assert.assertEquals(next.fencingToken(), 2L);
        Assert.assertTrue(next.valid());
      }
    } finally {
      winners.forEach(ResourceLease::close);
      pool.shutdownNow();
    }
  }

  @Test
  public void staleOwnerCannotRenewOrReleaseReplacement() throws Exception {
    var source = database();
    var provider = new JdbcResourceLeaseProvider(source, "testkit_resource_lease");
    var old = provider.tryAcquire("qa:job:42", Duration.ofSeconds(30)).orElseThrow();
    try (var connection = source.getConnection();
        var statement =
            connection.prepareStatement(
                "UPDATE testkit_resource_lease SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE resource_key=?")) {
      statement.setString(1, "qa:job:42");
      statement.executeUpdate();
    }
    try (var next = provider.tryAcquire("qa:job:42", Duration.ofSeconds(30)).orElseThrow()) {
      Assert.assertFalse(old.valid());
      Assert.assertFalse(old.renew(Duration.ofSeconds(30)));
      old.close();
      Assert.assertTrue(next.valid());
      Assert.assertEquals(next.fencingToken(), 2L);
    } finally {
      old.close();
    }
    Assert.expectThrows(
        IllegalArgumentException.class,
        () -> new JdbcResourceLeaseProvider(source, "unsafe; DROP TABLE x"));
    Assert.expectThrows(
        IllegalArgumentException.class, () -> provider.tryAcquire("key", Duration.ofMillis(1)));
  }
}
