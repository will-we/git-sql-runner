package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.domain.sql.ExecutionRecord;
import com.example.gitsqlrunner.domain.sql.ExecutionRecordRepository;
import com.example.gitsqlrunner.domain.sql.SqlFile;
import com.example.gitsqlrunner.domain.sql.SqlFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class SqlExecutionService {
  private final SqlFileRepository sqlFileRepository;
  private final ExecutionRecordRepository recordRepository;
  private final JdbcTemplate metaJdbcTemplate;
  private final TargetDbExecutorRegistry targetDbExecutorRegistry;

  public enum TxMode { FILE, STATEMENT }

  public SqlExecutionService(SqlFileRepository sqlFileRepository,
                             ExecutionRecordRepository recordRepository,
                             JdbcTemplate metaJdbcTemplate,
                             TargetDbExecutorRegistry targetDbExecutorRegistry) {
    this.sqlFileRepository = sqlFileRepository;
    this.recordRepository = recordRepository;
    this.metaJdbcTemplate = metaJdbcTemplate;
    this.targetDbExecutorRegistry = targetDbExecutorRegistry;
  }

  public Map<String, Object> execute(int sqlFileId, TxMode txMode, ExecutionRecord.ExecutedBy executedBy, List<String> targetIds) {
    Optional<SqlFile> opt = sqlFileRepository.findById(sqlFileId);
    if (opt.isEmpty()) throw new IllegalArgumentException("sql file not found: " + sqlFileId);
    if (targetDbExecutorRegistry.isEmpty()) {
      throw new IllegalStateException("no target db configured");
    }
    List<String> normalizedTargetIds = normalizeTargetIds(targetIds);
    if (normalizedTargetIds.isEmpty()) {
      throw new IllegalArgumentException("targetIds required");
    }
    List<TargetDbExecutorRegistry.TargetDbExecutor> targets = normalizedTargetIds.stream()
      .map(targetDbExecutorRegistry::required)
      .toList();
    SqlFile file = opt.get();
    String content;
    try {
      content = Files.readString(Path.of(file.getFilePath()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    List<Map<String, Object>> results = new ArrayList<>();
    int successCount = 0;
    for (var target : targets) {
      int recordId = recordRepository.createRunning(file.getId(), target.id(), target.type(), executedBy, Instant.now());
      try {
        executeSql(target.jdbcTemplate(), content, txMode);
        recordRepository.markSuccess(recordId, Instant.now());
        successCount++;
        results.add(targetResult(target, "SUCCESS", null));
      } catch (Exception e) {
        String error = safeMessage(e);
        recordRepository.markFailed(recordId, Instant.now(), error);
        results.add(targetResult(target, "FAILED", error));
      }
    }
    boolean allSuccess = successCount == targets.size();
    sqlFileRepository.markExecuted(file.getId(), allSuccess ? SqlFile.Status.SUCCESS : SqlFile.Status.FAILED);
    Map<String, Object> summary = new HashMap<>();
    summary.put("ok", allSuccess);
    summary.put("totalCount", targets.size());
    summary.put("successCount", successCount);
    summary.put("failedCount", targets.size() - successCount);
    summary.put("results", results);
    return summary;
  }

  public Map<String, Object> checkSyntax(String sqlContent) {
    List<String> parts = splitSql(sqlContent);
    List<Map<String, Object>> issues = new ArrayList<>();
    int[] statementCount = {0};
    metaJdbcTemplate.execute((ConnectionCallback<Object>) (con) -> {
      for (int i = 0; i < parts.size(); i++) {
        String stmt = parts.get(i);
        if (stmt == null || stmt.isBlank()) continue;
        statementCount[0]++;
        try (var ps = con.prepareStatement(stmt)) {
        } catch (Exception e) {
          Map<String, Object> item = new HashMap<>();
          String msg = safeMessage(e);
          item.put("index", i + 1);
          item.put("message", msg);
          item.put("token", extractNearToken(msg));
          item.put("sql", snippet(stmt));
          issues.add(item);
        }
      }
      return null;
    });
    Map<String, Object> result = new HashMap<>();
    result.put("ok", issues.isEmpty());
    result.put("statementCount", statementCount[0]);
    result.put("issues", issues);
    return result;
  }

  private static void executeSql(JdbcTemplate jdbcTemplate, String content, TxMode txMode) {
    if (txMode == TxMode.FILE) {
      jdbcTemplate.execute((ConnectionCallback<Object>) (con) -> {
        con.setAutoCommit(false);
        try (var st = con.createStatement()) {
          for (String s : splitSql(content)) {
            if (!s.isBlank()) st.execute(s);
          }
          con.commit();
        } catch (Exception e) {
          log.error("rollback due to error: {}", safeMessage(e), e);
          throw e;
        }
        return null;
      });
      return;
    }
    for (String s : splitSql(content)) {
      if (!s.isBlank()) jdbcTemplate.execute(s);
    }
  }

  private static List<String> normalizeTargetIds(List<String> targetIds) {
    if (targetIds == null) return List.of();
    LinkedHashSet<String> dedup = new LinkedHashSet<>();
    for (String targetId : targetIds) {
      if (targetId == null) continue;
      String v = targetId.trim();
      if (!v.isEmpty()) dedup.add(v);
    }
    return dedup.stream().toList();
  }

  private static Map<String, Object> targetResult(TargetDbExecutorRegistry.TargetDbExecutor target, String status, String errorMessage) {
    Map<String, Object> map = new HashMap<>();
    map.put("targetDbId", target.id());
    map.put("targetDbLabel", target.label());
    map.put("targetDbType", target.type());
    map.put("databaseName", target.databaseName());
    map.put("status", status);
    map.put("errorMessage", errorMessage);
    return map;
  }

  private static String safeMessage(Throwable e) {
    Throwable core = mostSpecificCause(e);
    if (core instanceof SQLException sqlException) {
      String message = sqlException.getMessage();
      if (message == null || message.isBlank()) {
        message = sqlException.getClass().getSimpleName();
      }
      String sqlState = sqlException.getSQLState();
      int errorCode = sqlException.getErrorCode();
      if ((sqlState == null || sqlState.isBlank()) && errorCode == 0) {
        return message;
      }
      return message + " [SQLState=" + valueOrUnknown(sqlState) + ", ErrorCode=" + errorCode + "]";
    }
    if (core.getMessage() == null || core.getMessage().isBlank()) {
      return core.getClass().getSimpleName();
    }
    return core.getMessage();
  }

  private static Throwable mostSpecificCause(Throwable throwable) {
    if (throwable == null) return new RuntimeException("UnknownException");
    if (throwable instanceof org.springframework.dao.DataAccessException dataAccessException) {
      Throwable specific = dataAccessException.getMostSpecificCause();
      if (specific != null && specific != dataAccessException) {
        return rootCause(specific);
      }
    }
    return rootCause(throwable);
  }

  private static Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private static String valueOrUnknown(String value) {
    if (value == null || value.isBlank()) return "UNKNOWN";
    return value;
  }

  private static String snippet(String sql) {
    String s = sql.replace('\n', ' ').replace('\r', ' ').trim();
    if (s.length() <= 120) return s;
    return s.substring(0, 120) + "...";
  }

  private static String extractNearToken(String msg) {
    if (msg == null) return null;
    int nearIdx = msg.indexOf("near \"");
    if (nearIdx < 0) return null;
    int start = nearIdx + 6;
    int end = msg.indexOf('"', start);
    if (end <= start) return null;
    String token = msg.substring(start, end).trim();
    return token.isBlank() ? null : token;
  }

  private static java.util.List<String> splitSql(String sql) {
    java.util.List<String> res = new java.util.ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inSingle = false, inDouble = false;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '\'' && !inDouble) inSingle = !inSingle;
      if (c == '"' && !inSingle) inDouble = !inDouble;
      if (c == ';' && !inSingle && !inDouble) {
        res.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    if (!cur.isEmpty()) res.add(cur.toString());
    return res;
  }
}

