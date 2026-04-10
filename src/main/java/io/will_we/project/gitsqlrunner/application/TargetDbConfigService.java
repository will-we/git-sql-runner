package io.will_we.project.gitsqlrunner.application;

import io.will_we.project.gitsqlrunner.domain.sql.TargetDatabase;
import io.will_we.project.gitsqlrunner.domain.sql.TargetDatabaseRepository;
import io.will_we.project.gitsqlrunner.support.CompatText;
import io.will_we.project.gitsqlrunner.support.ErrorMessageSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TargetDbConfigService {
  private final TargetDatabaseRepository repository;
  private final TargetDbViewAssembler targetDbViewAssembler;

  public TargetDbConfigService(TargetDatabaseRepository repository, TargetDbViewAssembler targetDbViewAssembler) {
    this.repository = repository;
    this.targetDbViewAssembler = targetDbViewAssembler;
  }

  public List<Map<String, Object>> listAll() {
    List<Map<String, Object>> result = repository.findAll().stream().map(targetDbViewAssembler::toView).collect(Collectors.toList());
    log.info("target config list: count={}", result.size());
    return result;
  }

  public Map<String, Object> create(TargetDbPayload payload) {
    TargetDbPayload normalized = normalizePayload(payload, null);
    Instant now = Instant.now();
    TargetDatabase entity = new TargetDatabase(
      null,
      normalized.targetId(),
      normalized.name(),
      normalized.dbType(),
      normalized.driverClassName(),
      normalized.driverJarPath(),
      normalized.host(),
      normalized.port(),
      normalized.databaseName(),
      normalized.username(),
      normalized.password(),
      normalized.jdbcParams(),
      normalized.enabled(),
      now,
      now
    );
    int id = repository.create(entity);
    log.info("target config created: id={}, targetId={}, dbType={}, host={}, port={}, databaseName={}, enabled={}",
      id, entity.getTargetId(), entity.getDbType(), entity.getHost(), entity.getPort(), entity.getDatabaseName(), entity.isEnabled());
    return repository.findById(id).map(targetDbViewAssembler::toView).orElseThrow(() -> new IllegalStateException("target created but not found: " + id));
  }

  public Map<String, Object> update(int id, TargetDbPayload payload) {
    TargetDatabase old = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("target not found: " + id));
    TargetDbPayload normalized = normalizePayload(payload, old);
    TargetDatabase next = new TargetDatabase(
      id,
      normalized.targetId(),
      normalized.name(),
      normalized.dbType(),
      normalized.driverClassName(),
      normalized.driverJarPath(),
      normalized.host(),
      normalized.port(),
      normalized.databaseName(),
      normalized.username(),
      normalized.password(),
      normalized.jdbcParams(),
      normalized.enabled(),
      old.getCreatedAt(),
      Instant.now()
    );
    repository.update(next);
    log.info("target config updated: id={}, targetId={}, dbType={}, host={}, port={}, databaseName={}, enabled={}",
      id, next.getTargetId(), next.getDbType(), next.getHost(), next.getPort(), next.getDatabaseName(), next.isEnabled());
    return repository.findById(id).map(targetDbViewAssembler::toView).orElseThrow(() -> new IllegalStateException("target updated but not found: " + id));
  }

  public void delete(int id) {
    TargetDatabase targetDatabase = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("target not found: " + id));
    repository.deleteById(id);
    log.info("target config deleted: id={}, targetId={}, dbType={}, databaseName={}",
      id, targetDatabase.getTargetId(), targetDatabase.getDbType(), targetDatabase.getDatabaseName());
  }

  public Map<String, Object> testConnection(TargetDbPayload payload) {
    log.info("target config test start: targetId={}, dbType={}, host={}, port={}, databaseName={}",
      payload.targetId(), payload.dbType(), payload.host(), payload.port(), payload.databaseName());
    return testConnectionInternal(normalizePayload(payload, null));
  }

  public Map<String, Object> testConnectionById(int id) {
    TargetDatabase item = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("target not found: " + id));
    log.info("target config test by id start: id={}, targetId={}, dbType={}, host={}, port={}, databaseName={}",
      item.getId(), item.getTargetId(), item.getDbType(), item.getHost(), item.getPort(), item.getDatabaseName());
    return testConnectionInternal(normalizePayload(new TargetDbPayload(
      item.getTargetId(),
      item.getName(),
      item.getDbType(),
      item.getDriverClassName(),
      item.getDriverJarPath(),
      item.getHost(),
      item.getPort(),
      item.getDatabaseName(),
      item.getUsername(),
      item.getPassword(),
      item.getJdbcParams(),
      item.isEnabled()
    ), item));
  }

  private TargetDbPayload normalizePayload(TargetDbPayload payload, TargetDatabase current) {
    String targetId = TargetDbConnectionSupport.normalizeRequired(payload.targetId(), "targetId");
    String name = TargetDbConnectionSupport.normalizeRequired(payload.name(), "name");
    String dbType = TargetDbConnectionSupport.normalizeType(payload.dbType());
    String driverClassName = CompatText.trimToNull(payload.driverClassName());
    String driverJarPath = CompatText.trimToNull(payload.driverJarPath());
    if (driverJarPath != null && driverClassName == null) {
      log.warn("target config invalid driver settings: targetId={}, driverJarPath={}, driverClassName missing", targetId, driverJarPath);
      throw new IllegalArgumentException("driverClassName required when driverJarPath provided");
    }
    String host = TargetDbConnectionSupport.normalizeRequired(payload.host(), "host");
    String databaseName = TargetDbConnectionSupport.normalizeRequired(payload.databaseName(), "databaseName");
    String username = TargetDbConnectionSupport.normalizeRequired(payload.username(), "username");
    if (payload.port() == null || payload.port().intValue() <= 0) {
      log.warn("target config invalid port: targetId={}, port={}", targetId, payload.port());
      throw new IllegalArgumentException("port invalid");
    }
    String password = CompatText.trimToNull(payload.password());
    if (current != null && password == null) {
      password = current.getPassword();
    }
    String jdbcParams = TargetDbConnectionSupport.normalizeJdbcParams(payload.jdbcParams());
    repository.findByTargetId(targetId).ifPresent(exist -> {
      if (current == null || !exist.getId().equals(current.getId())) {
        log.warn("target config duplicated targetId: targetId={}, currentId={}, existId={}",
          targetId, current == null ? null : current.getId(), exist.getId());
        throw new IllegalArgumentException("targetId duplicated");
      }
    });
    return new TargetDbPayload(targetId, name, dbType, driverClassName, driverJarPath, host, payload.port(), databaseName, username, password == null ? "" : password, jdbcParams, payload.enabled());
  }

  private static Map<String, Object> testConnectionInternal(TargetDbPayload payload) {
    Map<String, Object> result = new HashMap<String, Object>();
    try (java.sql.Connection con = TargetDbConnectionSupport.createDataSource(
      payload.dbType(),
      payload.driverClassName(),
      payload.driverJarPath(),
      payload.host(),
      payload.port(),
      payload.databaseName(),
      payload.username(),
      payload.password(),
      payload.jdbcParams()
    ).getConnection()) {
      result.put("ok", true);
      result.put("message", "连接成功");
      result.put("dbProduct", con.getMetaData().getDatabaseProductName());
      log.info("target config test success: targetId={}, dbType={}, host={}, port={}, databaseName={}, dbProduct={}",
        payload.targetId(), payload.dbType(), payload.host(), payload.port(), payload.databaseName(), con.getMetaData().getDatabaseProductName());
      return result;
    } catch (Exception e) {
      result.put("ok", false);
      result.put("message", ErrorMessageSupport.safeMessage(e));
      log.error("target config test failed: targetId={}, dbType={}, host={}, port={}, databaseName={}, error={}",
        payload.targetId(), payload.dbType(), payload.host(), payload.port(), payload.databaseName(), result.get("message"), e);
      return result;
    }
  }

  public static final class TargetDbPayload {
    private final String targetId;
    private final String name;
    private final String dbType;
    private final String driverClassName;
    private final String driverJarPath;
    private final String host;
    private final Integer port;
    private final String databaseName;
    private final String username;
    private final String password;
    private final String jdbcParams;
    private final boolean enabled;

    public TargetDbPayload(String targetId, String name, String dbType, String driverClassName, String driverJarPath, String host, Integer port, String databaseName, String username, String password, String jdbcParams, boolean enabled) {
      this.targetId = targetId;
      this.name = name;
      this.dbType = dbType;
      this.driverClassName = driverClassName;
      this.driverJarPath = driverJarPath;
      this.host = host;
      this.port = port;
      this.databaseName = databaseName;
      this.username = username;
      this.password = password;
      this.jdbcParams = jdbcParams;
      this.enabled = enabled;
    }

    public String targetId() {
      return targetId;
    }

    public String name() {
      return name;
    }

    public String dbType() {
      return dbType;
    }

    public String driverClassName() {
      return driverClassName;
    }

    public String driverJarPath() {
      return driverJarPath;
    }

    public String host() {
      return host;
    }

    public Integer port() {
      return port;
    }

    public String databaseName() {
      return databaseName;
    }

    public String username() {
      return username;
    }

    public String password() {
      return password;
    }

    public String jdbcParams() {
      return jdbcParams;
    }

    public boolean enabled() {
      return enabled;
    }
  }
}
