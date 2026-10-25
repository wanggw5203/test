package io.testkit.basetest.database;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JdbcSessionTest {
  @Test
  public void failedInitializationClosesOwnedConnectionAndPreservesBothFailures() {
    var initialization = new SQLException("transaction setup failed");
    var closing = new SQLException("connection close failed");
    AtomicInteger closes = new AtomicInteger();
    Connection connection =
        (Connection)
            Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("setAutoCommit")) throw initialization;
                  if (method.getName().equals("close")) {
                    closes.incrementAndGet();
                    throw closing;
                  }
                  throw new AssertionError("Unexpected connection operation: " + method.getName());
                });
    var failure = Assert.expectThrows(SQLException.class, () -> new JdbcSession(connection, 1, 10));
    Assert.assertSame(failure, initialization);
    Assert.assertEquals(closes.get(), 1);
    Assert.assertEquals(failure.getSuppressed(), new Throwable[] {closing});
  }

  @Test
  public void closeIsIdempotentAndContinuesAfterRollbackFailure() throws Exception {
    var rollback = new SQLException("rollback failed");
    AtomicInteger closes = new AtomicInteger();
    AtomicInteger rollbacks = new AtomicInteger();
    Connection connection =
        (Connection)
            Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) ->
                    switch (method.getName()) {
                      case "setAutoCommit" -> null;
                      case "rollback" -> {
                        rollbacks.incrementAndGet();
                        throw rollback;
                      }
                      case "close" -> {
                        closes.incrementAndGet();
                        yield null;
                      }
                      default ->
                          throw new AssertionError(
                              "Unexpected connection operation: " + method.getName());
                    });
    var session = new JdbcSession(connection, 1, 10);
    Assert.assertSame(Assert.expectThrows(SQLException.class, session::close), rollback);
    session.close();
    Assert.assertEquals(rollbacks.get(), 1);
    Assert.assertEquals(closes.get(), 1);
    Assert.expectThrows(IllegalStateException.class, session::commit);
  }
}
