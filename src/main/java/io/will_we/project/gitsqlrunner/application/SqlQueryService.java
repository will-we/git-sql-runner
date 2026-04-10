package io.will_we.project.gitsqlrunner.application;

import io.will_we.project.gitsqlrunner.domain.sql.ExecutionRecord;
import io.will_we.project.gitsqlrunner.domain.sql.ExecutionRecordRepository;
import io.will_we.project.gitsqlrunner.domain.sql.SqlFile;
import io.will_we.project.gitsqlrunner.domain.sql.SqlFileRepository;
import io.will_we.project.gitsqlrunner.support.CompatFiles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SqlQueryService {
  private final SqlFileRepository sqlFileRepository;
  private final ExecutionRecordRepository executionRecordRepository;
  private final TargetDbExecutorRegistry targetDbExecutorRegistry;
  private final SqlExecutionService sqlExecutionService;
  private final SqlFileTreeAssembler sqlFileTreeAssembler;
  private final ExecutionRecordViewAssembler executionRecordViewAssembler;
  private final String defaultBaseDir;
  private final String defaultSqlDir;

  public SqlQueryService(SqlFileRepository sqlFileRepository,
                         ExecutionRecordRepository executionRecordRepository,
                         TargetDbExecutorRegistry targetDbExecutorRegistry,
                         SqlExecutionService sqlExecutionService,
                         SqlFileTreeAssembler sqlFileTreeAssembler,
                         ExecutionRecordViewAssembler executionRecordViewAssembler,
                         @Value("${app.sync.base-dir:.}") String defaultBaseDir,
                         @Value("${app.sync.sql-dir:sql}") String defaultSqlDir) {
    this.sqlFileRepository = sqlFileRepository;
    this.executionRecordRepository = executionRecordRepository;
    this.targetDbExecutorRegistry = targetDbExecutorRegistry;
    this.sqlExecutionService = sqlExecutionService;
    this.sqlFileTreeAssembler = sqlFileTreeAssembler;
    this.executionRecordViewAssembler = executionRecordViewAssembler;
    this.defaultBaseDir = defaultBaseDir;
    this.defaultSqlDir = defaultSqlDir;
  }

  public List<SqlFile> listFiles(Boolean executed) {
    List<SqlFile> result = sqlFileRepository.findAll(executed);
    log.info("list files: executedFilter={}, count={}", executed, result.size());
    return result;
  }

  public Map<String, Object> syncDirSettings() {
    Map<String, Object> result = new HashMap<String, Object>();
    result.put("baseDir", defaultBaseDir);
    result.put("sqlDir", defaultSqlDir);
    return result;
  }

  public Map<String, Object> targetSettings() {
    Map<String, Object> result = new HashMap<String, Object>();
    List<Map<String, String>> targets = targetDbExecutorRegistry.listTargets();
    result.put("targets", targets);
    log.info("list target settings: count={}", targets.size());
    return result;
  }

  public Map<String, Object> filesTree(String baseDir, String sqlDir) {
    Path root = Paths.get(baseDir).resolve(sqlDir).normalize().toAbsolutePath();
    if (!Files.exists(root) || !Files.isDirectory(root)) {
      log.warn("build file tree skipped: baseDir={}, sqlDir={}, root={}", baseDir, sqlDir, root);
      return sqlFileTreeAssembler.emptyTree(root);
    }
    Map<String, SqlFile> indexed = sqlFileRepository.findAll(null).stream()
      .collect(Collectors.toMap(
        file -> Paths.get(file.getFilePath()).normalize().toAbsolutePath().toString(),
        file -> file,
        (left, right) -> left
      ));
    Map<String, Object> tree = sqlFileTreeAssembler.build(root, indexed);
    log.info("build file tree done: baseDir={}, sqlDir={}, root={}, indexedCount={}", baseDir, sqlDir, root, indexed.size());
    return tree;
  }

  public List<ExecutionRecord> listRecords(String status) {
    List<ExecutionRecord> records = executionRecordRepository.findAll(status);
    log.info("list records: statusFilter={}, count={}", status, records.size());
    return records;
  }

  public List<Map<String, Object>> recordsView(String status) {
    List<ExecutionRecord> records = executionRecordRepository.findAll(status);
    Map<Integer, SqlFile> fileMap = new HashMap<Integer, SqlFile>();
    for (SqlFile sqlFile : sqlFileRepository.findAll(null)) {
      fileMap.put(sqlFile.getId(), sqlFile);
    }
    List<Map<String, Object>> result = records.stream()
      .map(record -> executionRecordViewAssembler.toView(record, fileMap.get(record.getSqlFileId())))
      .collect(Collectors.toList());
    log.info("build record views: statusFilter={}, recordCount={}, fileCount={}", status, records.size(), fileMap.size());
    return result;
  }

  public Object preview(int id) {
    return sqlFileRepository.findById(id)
      .map(sqlFile -> {
        String content = CompatFiles.readUtf8(sqlFile.getFilePath());
        Map<String, Object> syntaxCheck = sqlExecutionService.checkSyntax(content);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", sqlFile.getId());
        result.put("fileName", sqlFile.getFileName());
        result.put("filePath", sqlFile.getFilePath());
        result.put("gitCommitId", sqlFile.getGitCommitId());
        result.put("content", content);
        result.put("syntaxCheck", syntaxCheck);
        log.info("preview sql file: id={}, fileName={}, path={}, contentLength={}, syntaxOk={}",
          sqlFile.getId(), sqlFile.getFileName(), sqlFile.getFilePath(), content.length(), syntaxCheck.get("ok"));
        return result;
      })
      .orElseGet(() -> {
        log.warn("preview sql file not found: id={}", id);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("error", "Not Found");
        return result;
      });
  }
}
