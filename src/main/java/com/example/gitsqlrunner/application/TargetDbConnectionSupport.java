package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.support.CompatText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class TargetDbConnectionSupport {
  private static final Logger log = LoggerFactory.getLogger(TargetDbConnectionSupport.class);
  private static final Set<String> SUPPORTED_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("mysql", "postgresql")));

  private TargetDbConnectionSupport() {
  }

  public static String normalizeType(String value) {
    String type = normalizeRequired(value, "dbType").toLowerCase(Locale.ROOT);
    if (!SUPPORTED_TYPES.contains(type)) {
      throw new IllegalArgumentException("dbType unsupported");
    }
    return type;
  }

  public static String normalizeRequired(String value, String field) {
    return CompatText.requireText(value, field);
  }

  public static String normalizeJdbcParams(String value) {
    return CompatText.trimToNull(value);
  }

  public static boolean isSupportedType(String value) {
    return value != null && SUPPORTED_TYPES.contains(value.toLowerCase(Locale.ROOT));
  }

  public static String buildJdbcUrl(String type, String host, int port, String databaseName, String jdbcParams) {
    String normalizedType = normalizeType(type);
    String base = "mysql".equals(normalizedType)
      ? "jdbc:mysql://" + host + ":" + port + "/" + databaseName
      : "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
    String params = normalizeJdbcParams(jdbcParams);
    if (params == null) {
      return base;
    }
    if (params.startsWith("?")) {
      return base + params;
    }
    if (params.startsWith("&")) {
      return base + "?" + params.substring(1);
    }
    return base + "?" + params;
  }

  public static DriverManagerDataSource createDataSource(String type,
                                                         String host,
                                                         int port,
                                                         String databaseName,
                                                         String username,
                                                         String password,
                                                         String jdbcParams) {
    String normalizedType = normalizeType(type);
    String url = buildJdbcUrl(normalizedType, host, port, databaseName, jdbcParams);
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("mysql".equals(normalizedType) ? "com.mysql.cj.jdbc.Driver" : "org.postgresql.Driver");
    dataSource.setUrl(url);
    dataSource.setUsername(normalizeRequired(username, "username"));
    dataSource.setPassword(password == null ? "" : password);
    log.info("target datasource prepared: dbType={}, host={}, port={}, databaseName={}, jdbcUrl={}",
      normalizedType, host, port, databaseName, maskJdbcUrl(url));
    return dataSource;
  }

  private static String maskJdbcUrl(String jdbcUrl) {
    if (jdbcUrl == null) {
      return "";
    }
    int questionIndex = jdbcUrl.indexOf('?');
    if (questionIndex < 0) {
      return jdbcUrl;
    }
    return jdbcUrl.substring(0, questionIndex) + "?***";
  }
}
