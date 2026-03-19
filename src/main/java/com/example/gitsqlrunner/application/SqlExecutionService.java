package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.domain.sql.ExecutionRecord;
import com.example.gitsqlrunner.domain.sql.ExecutionRecordRepository;
import com.example.gitsqlrunner.domain.sql.SqlFile;
import com.example.gitsqlrunner.domain.sql.SqlFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SqlExecutionService {
  private final SqlFileRepository sqlFileRepository;
  private final ExecutionRecordRepository recordRepository;
  private final JdbcTemplate jdbcTemplate;

  public enum TxMode { FILE, STATEMENT }

  public SqlExecutionService(SqlFileRepository sqlFileRepository,
                             ExecutionRecordRepository recordRepository,
                             JdbcTemplate jdbcTemplate) {
    this.sqlFileRepository = sqlFileRepository;
    this.recordRepository = recordRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  public void execute(int sqlFileId, TxMode txMode, ExecutionRecord.ExecutedBy executedBy) {
    Optional<SqlFile> opt = sqlFileRepository.findById(sqlFileId);
    if (opt.isEmpty()) throw new IllegalArgumentException("sql file not found: " + sqlFileId);
    SqlFile file = opt.get();
    if (file.isExecuted()) {
      throw new IllegalStateException("already executed");
    }
    int recordId = recordRepository.createRunning(file.getId(), executedBy, Instant.now());
    try {
      String content = Files.readString(Path.of(file.getFilePath()));
      if (txMode == TxMode.FILE) {
        jdbcTemplate.execute((ConnectionCallback<Object>) (con) -> {
          con.setAutoCommit(false);
          try (var st = con.createStatement()) {
            for (String s : splitSql(content)) {
              if (!s.isBlank()) st.execute(s);
            }
            con.commit();
          } catch (Exception e) {
            con.rollback();
            throw e;
          }
          return null;
        });
      } else {
        for (String s : splitSql(content)) {
          if (!s.isBlank()) jdbcTemplate.execute(s);
        }
      }
      recordRepository.markSuccess(recordId, Instant.now());
      sqlFileRepository.markExecuted(file.getId(), SqlFile.Status.SUCCESS);
    } catch (Exception e) {
      recordRepository.markFailed(recordId, Instant.now(), e.getMessage());
      sqlFileRepository.markExecuted(file.getId(), SqlFile.Status.FAILED);
      throw new RuntimeException(e);
    }
  }

  public Map<String, Object> checkSyntax(String sqlContent) {
    List<String> parts = splitSql(sqlContent);
    List<Map<String, Object>> issues = new ArrayList<>();
    int[] statementCount = {0};
    jdbcTemplate.execute((ConnectionCallback<Object>) (con) -> {
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

  private static String safeMessage(Exception e) {
    if (e.getMessage() == null || e.getMessage().isBlank()) {
      return e.getClass().getSimpleName();
    }
    return e.getMessage();
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
    if (cur.length() > 0) res.add(cur.toString());
    return res;
  }
}

