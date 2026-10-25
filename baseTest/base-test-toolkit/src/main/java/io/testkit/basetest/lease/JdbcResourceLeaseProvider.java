package io.testkit.basetest.lease;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Atomic SQL leases using the database clock and conditional owner updates.
 *
 * <p>The administrator installs {@code config/resource-leases.sql}; runtime permissions need
 * SELECT, INSERT and UPDATE only. Supply a dedicated data source that returns clean connections
 * because each statement commits independently. Statements have a five-second timeout; lease
 * durations range from one second to one hour. Connection acquisition timeouts belong to the
 * supplied pool configuration.
 *
 * <p>H2 behavior is covered by framework tests. Validate timestamp handling, unique-constraint SQL
 * states and concurrency with the production database dialect before enabling multiple CI hosts. No
 * background renewal is started.
 */
public final class JdbcResourceLeaseProvider implements ResourceLeaseProvider {
  private static final int QUERY_TIMEOUT_SECONDS = 5;
  private static final Duration MINIMUM_LEASE_DURATION = Duration.ofSeconds(1);
  private static final Duration MAXIMUM_LEASE_DURATION = Duration.ofHours(1);

  private final DataSource dataSource;
  private final String tableName;

  /** Creates a provider using an existing table named by a safe SQL identifier. */
  public JdbcResourceLeaseProvider(DataSource dataSource, String tableName) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    if (tableName == null || !tableName.matches("[A-Za-z][A-Za-z0-9_]{0,62}")) {
      throw new IllegalArgumentException("Invalid lease table identifier");
    }
    this.tableName = tableName;
  }

  private static void validateTtl(Duration ttl) {
    if (ttl == null
        || ttl.compareTo(MINIMUM_LEASE_DURATION) < 0
        || ttl.compareTo(MAXIMUM_LEASE_DURATION) > 0) {
      throw new IllegalArgumentException("Lease TTL must be between one second and one hour");
    }
  }

  private Connection openConnection() throws SQLException {
    Connection connection = dataSource.getConnection();
    try {
      if (!connection.getAutoCommit()) {
        connection.setAutoCommit(true);
      }
      return connection;
    } catch (SQLException failure) {
      connection.close();
      throw failure;
    }
  }

  private Timestamp expirationAtDatabaseTime(Connection connection, Duration ttl)
      throws SQLException {
    try (var statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP")) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      try (var result = statement.executeQuery()) {
        result.next();
        return Timestamp.from(result.getTimestamp(1).toInstant().plus(ttl));
      }
    }
  }

  /**
   * Acquires a resource without waiting, inserting a first generation or conditionally replacing an
   * expired owner. Resource keys are 1-160 ASCII letters, digits, colons, dots, underscores or
   * hyphens, starting with a letter or digit. Empty results represent contention, never SQL
   * failures.
   */
  @Override
  public Optional<ResourceLease> tryAcquire(String resourceKey, Duration ttl) {
    if (resourceKey == null || !resourceKey.matches("[A-Za-z0-9][A-Za-z0-9:._-]{0,159}")) {
      throw new IllegalArgumentException("Invalid lease resource key");
    }
    validateTtl(ttl);
    String ownerToken = UUID.randomUUID().toString();
    try (var connection = openConnection()) {
      Timestamp expiresAt = expirationAtDatabaseTime(connection, ttl);
      try (var insert =
          connection.prepareStatement(
              "INSERT INTO "
                  + tableName
                  + " (resource_key,owner_token,expires_at,fence) VALUES (?,?,?,1)")) {
        insert.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        insert.setString(1, resourceKey);
        insert.setString(2, ownerToken);
        insert.setTimestamp(3, expiresAt);
        try {
          insert.executeUpdate();
          return Optional.of(new Lease(resourceKey, ownerToken, 1));
        } catch (SQLException conflict) {
          if (conflict.getSQLState() == null || !conflict.getSQLState().startsWith("23")) {
            throw conflict;
          }
        }
      }
      try (var update =
          connection.prepareStatement(
              "UPDATE "
                  + tableName
                  + " SET owner_token=?,expires_at=?,fence=fence+1 WHERE resource_key=? AND expires_at<=CURRENT_TIMESTAMP")) {
        update.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        update.setString(1, ownerToken);
        update.setTimestamp(2, expiresAt);
        update.setString(3, resourceKey);
        if (update.executeUpdate() == 0) {
          return Optional.empty();
        }
      }
      try (var select =
          connection.prepareStatement(
              "SELECT fence FROM "
                  + tableName
                  + " WHERE resource_key=? AND owner_token=? AND expires_at>CURRENT_TIMESTAMP")) {
        select.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        select.setString(1, resourceKey);
        select.setString(2, ownerToken);
        try (var result = select.executeQuery()) {
          return result.next()
              ? Optional.of(new Lease(resourceKey, ownerToken, result.getLong(1)))
              : Optional.empty();
        }
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("Cannot acquire JDBC resource lease", failure);
    }
  }

  private final class Lease implements ResourceLease {
    private final String resourceKey;
    private final String ownerToken;
    private final long fencingToken;
    private boolean closed;

    private Lease(String resourceKey, String ownerToken, long fencingToken) {
      this.resourceKey = resourceKey;
      this.ownerToken = ownerToken;
      this.fencingToken = fencingToken;
    }

    @Override
    public long fencingToken() {
      return fencingToken;
    }

    @Override
    public synchronized boolean valid() {
      if (closed) {
        return false;
      }
      try (var connection = openConnection();
          var select =
              connection.prepareStatement(
                  "SELECT fence FROM "
                      + tableName
                      + " WHERE resource_key=? AND owner_token=? AND fence=? AND expires_at>CURRENT_TIMESTAMP")) {
        select.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        select.setString(1, resourceKey);
        select.setString(2, ownerToken);
        select.setLong(3, fencingToken);
        try (var result = select.executeQuery()) {
          return result.next();
        }
      } catch (SQLException failure) {
        throw new IllegalStateException("Cannot validate JDBC resource lease", failure);
      }
    }

    @Override
    public synchronized boolean renew(Duration ttl) {
      validateTtl(ttl);
      if (closed) {
        return false;
      }
      try (var connection = openConnection();
          var update =
              connection.prepareStatement(
                  "UPDATE "
                      + tableName
                      + " SET expires_at=? WHERE resource_key=? AND owner_token=? AND fence=? AND expires_at>CURRENT_TIMESTAMP")) {
        update.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        update.setTimestamp(1, expirationAtDatabaseTime(connection, ttl));
        update.setString(2, resourceKey);
        update.setString(3, ownerToken);
        update.setLong(4, fencingToken);
        return update.executeUpdate() == 1;
      } catch (SQLException failure) {
        throw new IllegalStateException("Cannot renew JDBC resource lease", failure);
      }
    }

    @Override
    public synchronized void close() {
      if (closed) {
        return;
      }
      try (var connection = openConnection();
          var update =
              connection.prepareStatement(
                  "UPDATE "
                      + tableName
                      + " SET owner_token=NULL,expires_at=CURRENT_TIMESTAMP WHERE resource_key=? AND owner_token=? AND fence=?")) {
        update.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        update.setString(1, resourceKey);
        update.setString(2, ownerToken);
        update.setLong(3, fencingToken);
        update.executeUpdate();
        closed = true;
      } catch (SQLException failure) {
        throw new IllegalStateException("Cannot release JDBC resource lease", failure);
      }
    }

    @Override
    public String toString() {
      return "ResourceLease[fence=" + fencingToken + ", closed=" + closed + "]";
    }
  }
}
