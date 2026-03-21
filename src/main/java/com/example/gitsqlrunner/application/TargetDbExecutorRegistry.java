package com.example.gitsqlrunner.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TargetDbExecutorRegistry {
  private static final Set<String> SUPPORTED_TYPES = Set.of("mysql", "postgresql");
  private final JdbcTemplate metaJdbcTemplate;

  public TargetDbExecutorRegistry(JdbcTemplate metaJdbcTemplate) {
    this.metaJdbcTemplate = metaJdbcTemplate;
  }

  public List<Map<String, String>> listTargets() {
    return metaJdbcTemplate.query(
      "SELECT target_id, name, db_type, database_name FROM target_database WHERE enabled=1 ORDER BY id DESC",
      (rs, rowNum) -> Map.of(
        "id", rs.getString("target_id"),
        "label", rs.getString("name"),
        "type", rs.getString("db_type"),
        "databaseName", rs.getString("database_name")
      )
    );
  }

  public TargetDbExecutor required(String targetId) {
    var list = metaJdbcTemplate.query(
      "SELECT target_id, name, db_type, host, port, database_name, username, password, jdbc_params " +
        "FROM target_database WHERE enabled=1 AND target_id=?",
      (rs, rowNum) -> {
        String type = normalizeType(rs.getString("db_type"));
        String host = normalizeRequired(rs.getString("host"), "target_database.host");
        int port = rs.getInt("port");
        String databaseName = normalizeRequired(rs.getString("database_name"), "target_database.database_name");
        String jdbcParams = rs.getString("jdbc_params");
        String url = buildJdbcUrl(type, host, port, databaseName, jdbcParams);
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("mysql".equals(type) ? "com.mysql.cj.jdbc.Driver" : "org.postgresql.Driver");
        ds.setUrl(url);
        ds.setUsername(normalizeRequired(rs.getString("username"), "target_database.username"));
        ds.setPassword(rs.getString("password") == null ? "" : rs.getString("password"));
        return new TargetDbExecutor(
          rs.getString("target_id"),
          rs.getString("name"),
          type,
          databaseName,
          new JdbcTemplate(ds)
        );
      },
      targetId
    );
    if (list.isEmpty()) {
      throw new IllegalArgumentException("unknown targetId: " + targetId);
    }
    return list.get(0);
  }

  public boolean isEmpty() {
    Integer count = metaJdbcTemplate.queryForObject("SELECT COUNT(1) FROM target_database WHERE enabled=1", Integer.class);
    return count == null || count <= 0;
  }

  private static String buildJdbcUrl(String type, String host, int port, String databaseName, String jdbcParams) {
    String base = "mysql".equals(type)
      ? "jdbc:mysql://" + host + ":" + port + "/" + databaseName
      : "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
    if (jdbcParams == null || jdbcParams.isBlank()) return base;
    String params = jdbcParams.trim();
    if (params.startsWith("?")) return base + params;
    if (params.startsWith("&")) return base + "?" + params.substring(1);
    return base + "?" + params;
  }

  private static String normalizeType(String value) {
    String type = normalizeRequired(value, "target_database.db_type").toLowerCase(Locale.ROOT);
    if (!SUPPORTED_TYPES.contains(type)) {
      throw new IllegalArgumentException("unsupported target db type: " + type);
    }
    return type;
  }

  private static String normalizeRequired(String value, String key) {
    String v = value == null ? "" : value.trim();
    if (v.isEmpty()) {
      throw new IllegalArgumentException("missing required config: " + key);
    }
    return v;
  }

  public record TargetDbExecutor(String id, String label, String type, String databaseName, JdbcTemplate jdbcTemplate) {}
}
