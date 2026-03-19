package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.domain.sql.ExecutionRecord;
import com.example.gitsqlrunner.domain.sql.ExecutionRecordRepository;
import com.example.gitsqlrunner.domain.sql.SqlFile;
import com.example.gitsqlrunner.domain.sql.SqlFileRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

