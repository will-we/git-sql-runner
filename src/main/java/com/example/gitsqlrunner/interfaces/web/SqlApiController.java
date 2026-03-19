package com.example.gitsqlrunner.interfaces.web;

import com.example.gitsqlrunner.application.GitSyncUseCase;
import com.example.gitsqlrunner.application.SqlExecutionService;
import com.example.gitsqlrunner.domain.sql.ExecutionRecord;
import com.example.gitsqlrunner.domain.sql.ExecutionRecordRepository;
import com.example.gitsqlrunner.domain.sql.SqlFileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SqlApiController {
  private final SqlFileRepository fileRepo;
  private final ExecutionRecordRepository recordRepo;
  private final SqlExecutionService execService;
  private final GitSyncUseCase gitSync;

  public SqlApiController(SqlFileRepository fileRepo,
                          ExecutionRecordRepository recordRepo,
                          SqlExecutionService execService,
                          GitSyncUseCase gitSync) {
    this.fileRepo = fileRepo;
    this.recordRepo = recordRepo;
    this.execService = execService;
    this.gitSync = gitSync;
  }

  @GetMapping("/files")
  public Object files(@RequestParam(name = "executed", required = false) Boolean executed) {
    return fileRepo.findAll(executed);
  }

  @PostMapping("/files/{id}/execute")
  public ResponseEntity<?> execute(@PathVariable("id") int id,
                                   @RequestParam(name = "mode", defaultValue = "FILE") String mode) {
    execService.execute(id, SqlExecutionService.TxMode.valueOf(mode), ExecutionRecord.ExecutedBy.MANUAL);
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @GetMapping("/records")
  public Object records(@RequestParam(name = "status", required = false) String status) {
    return recordRepo.findAll(status);
  }

  @PostMapping("/sync/local")
  public ResponseEntity<?> syncLocal(@RequestParam(name = "baseDir", defaultValue = ".") String baseDir,
                                     @RequestParam(name = "sqlDir", defaultValue = "sql") String sqlDir) {
    gitSync.indexLocalSqlDir(baseDir, sqlDir);
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @GetMapping("/files/{id}/preview")
  public Object preview(@PathVariable("id") int id) {
    return fileRepo.findById(id)
      .map(f -> {
        try {
          String content = Files.readString(Path.of(f.getFilePath()));
          return Map.of(
            "id", f.getId(),
            "fileName", f.getFileName(),
            "filePath", f.getFilePath(),
            "gitCommitId", f.getGitCommitId(),
            "content", content
          );
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      })
      .orElseGet(() -> Map.of("error", "Not Found"));
  }
}

