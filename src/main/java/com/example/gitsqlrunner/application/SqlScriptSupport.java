package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.support.CompatCollections;
import com.example.gitsqlrunner.support.CompatFiles;
import com.example.gitsqlrunner.support.CompatText;
import com.example.gitsqlrunner.support.ErrorMessageSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SqlScriptSupport {
  public String readUtf8(String filePath) {
    return CompatFiles.readUtf8(filePath);
  }

  public List<String> normalizeTargetIds(List<String> targetIds) {
    return CompatCollections.distinctNonBlank(targetIds);
  }

  public Map<String, Object> checkSyntax(JdbcTemplate jdbcTemplate, String sqlContent) {
    List<String> parts = splitSql(sqlContent);
    List<Map<String, Object>> issues = new ArrayList<Map<String, Object>>();
    int[] statementCount = {0};
    log.info("sql syntax check start: statementCandidateCount={}", parts.size());
    jdbcTemplate.execute((ConnectionCallback<Object>) con -> {
      for (int index = 0; index < parts.size(); index++) {
        String statement = parts.get(index);
        if (CompatText.isBlank(statement)) {
          continue;
        }
        statementCount[0]++;
        try (java.sql.PreparedStatement ignored = con.prepareStatement(statement)) {
        } catch (Exception e) {
          Map<String, Object> issue = new HashMap<String, Object>();
          String message = ErrorMessageSupport.safeMessage(e);
          issue.put("index", index + 1);
          issue.put("message", message);
          issue.put("token", ErrorMessageSupport.extractNearToken(message));
          issue.put("sql", ErrorMessageSupport.snippet(statement, 120));
          issues.add(issue);
        }
      }
      return null;
    });
    Map<String, Object> result = new HashMap<String, Object>();
    result.put("ok", issues.isEmpty());
    result.put("statementCount", statementCount[0]);
    result.put("issues", issues);
    log.info("sql syntax check done: statementCount={}, issueCount={}, ok={}", statementCount[0], issues.size(), issues.isEmpty());
    return result;
  }

  public void execute(JdbcTemplate jdbcTemplate, String sqlContent, SqlExecutionService.TxMode txMode) {
    List<String> statements = splitSql(sqlContent);
    int executableCount = countExecutable(statements);
    log.info("sql script execute start: txMode={}, statementCount={}", txMode, executableCount);
    if (txMode == SqlExecutionService.TxMode.FILE) {
      jdbcTemplate.execute((ConnectionCallback<Object>) con -> {
        con.setAutoCommit(false);
        try (java.sql.Statement statement = con.createStatement()) {
          int statementIndex = 0;
          for (String sql : statements) {
            if (!CompatText.isBlank(sql)) {
              statementIndex++;
              statement.execute(sql);
            }
          }
          con.commit();
          log.info("sql script execute committed: txMode={}, statementCount={}", txMode, executableCount);
        } catch (Exception e) {
          try {
            con.rollback();
            log.warn("sql script rollback done: txMode={}, statementCount={}", txMode, executableCount);
          } catch (Exception rollbackException) {
            log.error("sql script rollback failed: txMode={}, statementCount={}", txMode, executableCount, rollbackException);
          }
          log.error("sql script execute failed: txMode={}, statementCount={}, error={}", txMode, executableCount, ErrorMessageSupport.safeMessage(e), e);
          throw e;
        }
        return null;
      });
      return;
    }
    for (String sql : statements) {
      if (!CompatText.isBlank(sql)) {
        jdbcTemplate.execute(sql);
      }
    }
    log.info("sql script execute done: txMode={}, statementCount={}", txMode, executableCount);
  }

  public List<String> splitSql(String sql) {
    List<String> result = new ArrayList<String>();
    StringBuilder current = new StringBuilder();
    boolean inSingle = false;
    boolean inDouble = false;
    for (int index = 0; index < sql.length(); index++) {
      char value = sql.charAt(index);
      if (value == '\'' && !inDouble) {
        inSingle = !inSingle;
      }
      if (value == '"' && !inSingle) {
        inDouble = !inDouble;
      }
      if (value == ';' && !inSingle && !inDouble) {
        result.add(current.toString());
        current.setLength(0);
      } else {
        current.append(value);
      }
    }
    if (current.length() > 0) {
      result.add(current.toString());
    }
    return result;
  }

  private int countExecutable(List<String> statements) {
    int count = 0;
    for (String statement : statements) {
      if (!CompatText.isBlank(statement)) {
        count++;
      }
    }
    return count;
  }
}
