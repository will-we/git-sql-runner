package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.support.CompatText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TargetDbExecutorRegistry {
  private final JdbcTemplate metaJdbcTemplate;

  public TargetDbExecutorRegistry(JdbcTemplate metaJdbcTemplate) {
    this.metaJdbcTemplate = metaJdbcTemplate;
  }

  public List<Map<String, String>> listTargets() {
    List<Map<String, String>> targets = metaJdbcTemplate.query(
      "SELECT target_id, name, db_type, database_name FROM target_database WHERE enabled=1 ORDER BY id DESC",
      (rs, rowNum) -> {
        Map<String, String> map = new HashMap<String, String>();
        map.put("id", rs.getString("target_id"));
        map.put("label", rs.getString("name"));
        map.put("type", rs.getString("db_type"));
        map.put("databaseName", rs.getString("database_name"));
        return map;
      }
    );
    log.info("registry list targets: count={}", targets.size());
    return targets;
  }

  public TargetDbExecutor required(String targetId) {
    log.info("registry resolve target start: targetId={}", targetId);
    List<TargetDbExecutor> list = metaJdbcTemplate.query(
      "SELECT target_id, name, db_type, host, port, database_name, username, password, jdbc_params " +
        "FROM target_database WHERE enabled=1 AND target_id=?",
      (rs, rowNum) -> {
        String type = normalizeTargetConfig(rs.getString("db_type"), "target_database.db_type");
        String host = normalizeTargetConfig(rs.getString("host"), "target_database.host");
        int port = rs.getInt("port");
        String databaseName = normalizeTargetConfig(rs.getString("database_name"), "target_database.database_name");
        return new TargetDbExecutor(
          rs.getString("target_id"),
          rs.getString("name"),
          type,
          databaseName,
          new JdbcTemplate(TargetDbConnectionSupport.createDataSource(
            type,
            host,
            port,
            databaseName,
            normalizeTargetConfig(rs.getString("username"), "target_database.username"),
            rs.getString("password"),
            rs.getString("jdbc_params")
          ))
        );
      },
      targetId
    );
    if (list.isEmpty()) {
      log.warn("registry resolve target failed: targetId={}", targetId);
      throw new IllegalArgumentException("unknown targetId: " + targetId);
    }
    log.info("registry resolve target success: targetId={}, targetType={}, databaseName={}",
      list.get(0).id(), list.get(0).type(), list.get(0).databaseName());
    return list.get(0);
  }

  public boolean isEmpty() {
    Integer count = metaJdbcTemplate.queryForObject("SELECT COUNT(1) FROM target_database WHERE enabled=1", Integer.class);
    return count == null || count <= 0;
  }

  private static String normalizeTargetConfig(String value, String key) {
    if ("target_database.db_type".equals(key)) {
      String normalized = CompatText.trimToNull(value);
      if (normalized == null) {
        throw new IllegalArgumentException("missing required config: " + key);
      }
      if (!TargetDbConnectionSupport.isSupportedType(normalized)) {
        throw new IllegalArgumentException("unsupported target db type: " + normalized.toLowerCase());
      }
      return TargetDbConnectionSupport.normalizeType(normalized);
    }
    try {
      return TargetDbConnectionSupport.normalizeRequired(value, key);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("missing required config: " + key);
    }
  }

  public static final class TargetDbExecutor {
    private final String id;
    private final String label;
    private final String type;
    private final String databaseName;
    private final JdbcTemplate jdbcTemplate;

    public TargetDbExecutor(String id, String label, String type, String databaseName, JdbcTemplate jdbcTemplate) {
      this.id = id;
      this.label = label;
      this.type = type;
      this.databaseName = databaseName;
      this.jdbcTemplate = jdbcTemplate;
    }

    public String id() {
      return id;
    }

    public String label() {
      return label;
    }

    public String type() {
      return type;
    }

    public String databaseName() {
      return databaseName;
    }

    public JdbcTemplate jdbcTemplate() {
      return jdbcTemplate;
    }
  }
}
