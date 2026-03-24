package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.domain.sql.ExecutionRecord;
import com.example.gitsqlrunner.domain.sql.ExecutionRecordRepository;
import com.example.gitsqlrunner.domain.sql.SqlFile;
import com.example.gitsqlrunner.domain.sql.SqlFileRepository;
import com.example.gitsqlrunner.support.ErrorMessageSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SqlExecutionService {
  private final SqlFileRepository sqlFileRepository;
  private final ExecutionRecordRepository recordRepository;
  private final JdbcTemplate metaJdbcTemplate;
  private final TargetDbExecutorRegistry targetDbExecutorRegistry;
  private final SqlScriptSupport sqlScriptSupport;

  public enum TxMode { FILE, STATEMENT }

  public SqlExecutionService(SqlFileRepository sqlFileRepository,
                             ExecutionRecordRepository recordRepository,
                             JdbcTemplate metaJdbcTemplate,
                             TargetDbExecutorRegistry targetDbExecutorRegistry,
                             SqlScriptSupport sqlScriptSupport) {
    this.sqlFileRepository = sqlFileRepository;
    this.recordRepository = recordRepository;
    this.metaJdbcTemplate = metaJdbcTemplate;
    this.targetDbExecutorRegistry = targetDbExecutorRegistry;
    this.sqlScriptSupport = sqlScriptSupport;
  }

  public Map<String, Object> execute(int sqlFileId, TxMode txMode, ExecutionRecord.ExecutedBy executedBy, List<String> targetIds) {
    long start = System.currentTimeMillis();
    Optional<SqlFile> opt = sqlFileRepository.findById(sqlFileId);
    if (!opt.isPresent()) throw new IllegalArgumentException("sql file not found: " + sqlFileId);
    if (targetDbExecutorRegistry.isEmpty()) {
      throw new IllegalStateException("no target db configured");
    }
    List<String> normalizedTargetIds = sqlScriptSupport.normalizeTargetIds(targetIds);
    if (normalizedTargetIds.isEmpty()) {
      throw new IllegalArgumentException("targetIds required");
    }
    List<TargetDbExecutorRegistry.TargetDbExecutor> targets = normalizedTargetIds.stream()
      .map(targetDbExecutorRegistry::required)
      .collect(Collectors.toList());
    SqlFile file = opt.get();
    log.info("sql execute start: sqlFileId={}, fileName={}, path={}, txMode={}, executedBy={}, targetCount={}, targetIds={}",
      file.getId(), file.getFileName(), file.getFilePath(), txMode, executedBy, targets.size(), normalizedTargetIds);
    String content = sqlScriptSupport.readUtf8(file.getFilePath());
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    int successCount = 0;
    for (TargetDbExecutorRegistry.TargetDbExecutor target : targets) {
      int recordId = recordRepository.createRunning(file.getId(), target.id(), target.type(), executedBy, Instant.now());
      long targetStart = System.currentTimeMillis();
      log.info("sql execute target start: sqlFileId={}, recordId={}, targetId={}, targetType={}, databaseName={}",
        file.getId(), recordId, target.id(), target.type(), target.databaseName());
      try {
        sqlScriptSupport.execute(target.jdbcTemplate(), content, txMode);
        recordRepository.markSuccess(recordId, Instant.now());
        successCount++;
        results.add(targetResult(target, "SUCCESS", null));
        log.info("sql execute target success: sqlFileId={}, recordId={}, targetId={}, costMs={}",
          file.getId(), recordId, target.id(), System.currentTimeMillis() - targetStart);
      } catch (Exception e) {
        String error = ErrorMessageSupport.safeMessage(e);
        recordRepository.markFailed(recordId, Instant.now(), error);
        results.add(targetResult(target, "FAILED", error));
        log.error("sql execute target failed: sqlFileId={}, recordId={}, targetId={}, costMs={}, error={}",
          file.getId(), recordId, target.id(), System.currentTimeMillis() - targetStart, error, e);
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
    log.info("sql execute done: sqlFileId={}, ok={}, successCount={}, failedCount={}, costMs={}",
      file.getId(), allSuccess, successCount, targets.size() - successCount, System.currentTimeMillis() - start);
    return summary;
  }

  public Map<String, Object> checkSyntax(String sqlContent) {
    return sqlScriptSupport.checkSyntax(metaJdbcTemplate, sqlContent);
  }

  private static Map<String, Object> targetResult(TargetDbExecutorRegistry.TargetDbExecutor target, String status, String errorMessage) {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("targetDbId", target.id());
    map.put("targetDbLabel", target.label());
    map.put("targetDbType", target.type());
    map.put("databaseName", target.databaseName());
    map.put("status", status);
    map.put("errorMessage", errorMessage);
    return map;
  }
}

