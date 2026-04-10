package io.will_we.project.gitsqlrunner.domain.sql;

import java.time.Instant;

public class TargetDatabase {
  private Integer id;
  private String targetId;
  private String name;
  private String dbType;
  private String driverClassName;
  private String driverJarPath;
  private String host;
  private Integer port;
  private String databaseName;
  private String username;
  private String password;
  private String jdbcParams;
  private boolean enabled;
  private Instant createdAt;
  private Instant updatedAt;

  public TargetDatabase(Integer id, String targetId, String name, String dbType, String driverClassName, String driverJarPath, String host, Integer port,
                        String databaseName, String username, String password, String jdbcParams, boolean enabled,
                        Instant createdAt, Instant updatedAt) {
    this.id = id;
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
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Integer getId() { return id; }
  public String getTargetId() { return targetId; }
  public String getName() { return name; }
  public String getDbType() { return dbType; }
  public String getDriverClassName() { return driverClassName; }
  public String getDriverJarPath() { return driverJarPath; }
  public String getHost() { return host; }
  public Integer getPort() { return port; }
  public String getDatabaseName() { return databaseName; }
  public String getUsername() { return username; }
  public String getPassword() { return password; }
  public String getJdbcParams() { return jdbcParams; }
  public boolean isEnabled() { return enabled; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
