package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.domain.sql.TargetDatabase;
import com.example.gitsqlrunner.domain.sql.TargetDatabaseRepository;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TargetDbConfigService {
  private static final List<String> SUPPORTED_TYPES = List.of("mysql", "postgresql");
  private final TargetDatabaseRepository repository;

  public TargetDbConfigService(TargetDatabaseRepository repository) {
    this.repository = repository;
  }

  public List<Map<String, Object>> listAll() {
    return repository.findAll().stream().map(this::toMap).toList();
  }

  public Map<String, Object> create(TargetDbPayload payload) {
    validatePayload(payload, null);
    Instant now = Instant.now();
    TargetDatabase entity = new TargetDatabase(
      null,
      payload.targetId().trim(),
      payload.name().trim(),
      payload.dbType().trim().toLowerCase(Locale.ROOT),
      payload.host().trim(),
      payload.port(),
      payload.databaseName().trim(),
      payload.username().trim(),
      payload.password() == null ? "" : payload.password(),
      normalizeJdbcParams(payload.jdbcParams()),
      payload.enabled(),
      now,
      now
    );
    int id = repository.create(entity);
    return repository.findById(id).map(this::toMap).orElseThrow();
  }

  public Map<String, Object> update(int id, TargetDbPayload payload) {
    TargetDatabase old = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("target not found: " + id));
    String nextPassword = payload.password() == null || payload.password().isBlank() ? old.getPassword() : payload.password();
    TargetDbPayload normalized = new TargetDbPayload(
      payload.targetId(), payload.name(), payload.dbType(), payload.host(), payload.port(),
      payload.databaseName(), payload.username(), nextPassword, payload.jdbcParams(), payload.enabled()
    );
    validatePayload(normalized, id);
    TargetDatabase next = new TargetDatabase(
      id,
      normalized.targetId().trim(),
      normalized.name().trim(),
      normalized.dbType().trim().toLowerCase(Locale.ROOT),
      normalized.host().trim(),
      normalized.port(),
      normalized.databaseName().trim(),
      normalized.username().trim(),
      nextPassword,
      normalizeJdbcParams(normalized.jdbcParams()),
      normalized.enabled(),
      old.getCreatedAt(),
      Instant.now()
    );
    repository.update(next);
    return repository.findById(id).map(this::toMap).orElseThrow();
  }

  public void delete(int id) {
    repository.findById(id).orElseThrow(() -> new IllegalArgumentException("target not found: " + id));
    repository.deleteById(id);
  }

  public Map<String, Object> testConnection(TargetDbPayload payload) {
    validatePayload(payload, null);
    return testConnectionInternal(payload);
  }

  public Map<String, Object> testConnectionById(int id) {
    TargetDatabase item = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("target not found: " + id));
    return testConnectionInternal(new TargetDbPayload(
      item.getTargetId(),
      item.getName(),
      item.getDbType(),
      item.getHost(),
      item.getPort(),
      item.getDatabaseName(),
      item.getUsername(),
      item.getPassword(),
      item.getJdbcParams(),
      item.isEnabled()
    ));
  }

  private void validatePayload(TargetDbPayload payload, Integer currentId) {
    require(payload.targetId(), "targetId");
    require(payload.name(), "name");
    require(payload.dbType(), "dbType");
    require(payload.host(), "host");
    require(payload.databaseName(), "databaseName");
    require(payload.username(), "username");
    if (payload.port() == null || payload.port() <= 0) {
      throw new IllegalArgumentException("port invalid");
    }
    String dbType = payload.dbType().trim().toLowerCase(Locale.ROOT);
    if (!SUPPORTED_TYPES.contains(dbType)) {
      throw new IllegalArgumentException("dbType unsupported");
    }
    repository.findByTargetId(payload.targetId().trim()).ifPresent(exist -> {
      if (currentId == null || !exist.getId().equals(currentId)) {
        throw new IllegalArgumentException("targetId duplicated");
      }
    });
  }

  private static void require(String v, String field) {
    if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(field + " required");
  }

  private static String normalizeJdbcParams(String jdbcParams) {
    return jdbcParams == null || jdbcParams.isBlank() ? null : jdbcParams.trim();
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

  private static Map<String, Object> testConnectionInternal(TargetDbPayload payload) {
    String dbType = payload.dbType().trim().toLowerCase(Locale.ROOT);
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("mysql".equals(dbType) ? "com.mysql.cj.jdbc.Driver" : "org.postgresql.Driver");
    ds.setUrl(buildJdbcUrl(dbType, payload.host().trim(), payload.port(), payload.databaseName().trim(), payload.jdbcParams()));
    ds.setUsername(payload.username().trim());
    ds.setPassword(payload.password() == null ? "" : payload.password());
    Map<String, Object> result = new HashMap<>();
    try (var con = ds.getConnection()) {
      result.put("ok", true);
      result.put("message", "连接成功");
      result.put("dbProduct", con.getMetaData().getDatabaseProductName());
      return result;
    } catch (Exception e) {
      result.put("ok", false);
      result.put("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      return result;
    }
  }

  private Map<String, Object> toMap(TargetDatabase v) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", v.getId());
    map.put("targetId", v.getTargetId());
    map.put("name", v.getName());
    map.put("dbType", v.getDbType());
    map.put("host", v.getHost());
    map.put("port", v.getPort());
    map.put("databaseName", v.getDatabaseName());
    map.put("username", v.getUsername());
    map.put("jdbcParams", v.getJdbcParams());
    map.put("enabled", v.isEnabled());
    map.put("createdAt", v.getCreatedAt() == null ? null : v.getCreatedAt().toString());
    map.put("updatedAt", v.getUpdatedAt() == null ? null : v.getUpdatedAt().toString());
    map.put("passwordSet", v.getPassword() != null && !v.getPassword().isBlank());
    return map;
  }

  public record TargetDbPayload(
    String targetId,
    String name,
    String dbType,
    String host,
    Integer port,
    String databaseName,
    String username,
    String password,
    String jdbcParams,
    boolean enabled
  ) {}
}
