package io.testkit.basetest.database;

import java.sql.*;
import java.util.*;

/**
 * Owned connection with prepared parameters and query limits; close rolls back uncommitted fixture
 * changes.
 */
public final class JdbcSession implements AutoCloseable {
  private final Connection connection;
  private final int timeoutSeconds;
  private final int maxRows;
  private boolean closed;

  public JdbcSession(Connection connection, int timeoutSeconds, int maxRows) throws SQLException {
    if (timeoutSeconds <= 0 || maxRows <= 0)
      throw new IllegalArgumentException("Positive SQL timeout/row limits required");
    this.connection = Objects.requireNonNull(connection);
    this.timeoutSeconds = timeoutSeconds;
    this.maxRows = maxRows;
    try {
      connection.setAutoCommit(false);
    } catch (SQLException | RuntimeException failure) {
      try {
        connection.close();
      } catch (SQLException close) {
        failure.addSuppressed(close);
      }
      throw failure;
    }
  }

  private PreparedStatement prepare(String sql, Object[] parameters) throws SQLException {
    if (closed) throw new IllegalStateException("JDBC session closed");
    Objects.requireNonNull(parameters, "SQL parameters required");
    PreparedStatement statement = connection.prepareStatement(sql);
    try {
      statement.setQueryTimeout(timeoutSeconds);
      statement.setMaxRows(maxRows);
      for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
      return statement;
    } catch (SQLException failure) {
      try {
        statement.close();
      } catch (SQLException close) {
        failure.addSuppressed(close);
      }
      throw failure;
    }
  }

  public List<Map<String, Object>> query(String sql, Object... parameters) throws SQLException {
    try (var statement = prepare(sql, parameters);
        var result = statement.executeQuery()) {
      List<Map<String, Object>> rows = new ArrayList<>();
      var columns = result.getMetaData();
      while (result.next()) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= columns.getColumnCount(); i++)
          row.put(columns.getColumnLabel(i), result.getObject(i));
        rows.add(Collections.unmodifiableMap(row));
      }
      return List.copyOf(rows);
    }
  }

  public int update(String sql, Object... parameters) throws SQLException {
    try (var statement = prepare(sql, parameters)) {
      return statement.executeUpdate();
    }
  }

  /**
   * Explicit commit for fixtures that must be visible to another process; register compensating
   * deletion separately.
   */
  public void commit() throws SQLException {
    if (closed) throw new IllegalStateException("JDBC session closed");
    connection.commit();
  }

  public void close() throws SQLException {
    if (closed) return;
    closed = true;
    SQLException failure = null;
    try {
      connection.rollback();
    } catch (SQLException e) {
      failure = e;
    }
    try {
      connection.close();
    } catch (SQLException e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    }
    if (failure != null) throw failure;
  }
}
