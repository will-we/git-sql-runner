package com.example.gitsqlrunner.interfaces.web;

import com.example.gitsqlrunner.application.GitSyncUseCase;
import com.example.gitsqlrunner.application.SqlQueryService;
import com.example.gitsqlrunner.application.SqlExecutionService;
import com.example.gitsqlrunner.domain.sql.ExecutionRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
public class SqlApiController {
    private final SqlQueryService sqlQueryService;
    private final SqlExecutionService sqlExecutionService;
    private final GitSyncUseCase gitSyncUseCase;

    public SqlApiController(SqlQueryService sqlQueryService,
                            SqlExecutionService sqlExecutionService,
                            GitSyncUseCase gitSyncUseCase) {
        this.sqlQueryService = sqlQueryService;
        this.sqlExecutionService = sqlExecutionService;
        this.gitSyncUseCase = gitSyncUseCase;
    }

    @GetMapping("/files")
    public Object files(@RequestParam(name = "executed", required = false) Boolean executed) {
        log.info("api list files: executedFilter={}", executed);
        return sqlQueryService.listFiles(executed);
    }

    @GetMapping("/settings/sync-dir")
    public Object syncDirSettings() {
        return sqlQueryService.syncDirSettings();
    }

    @GetMapping("/settings/targets")
    public Object targetSettings() {
        return sqlQueryService.targetSettings();
    }

    @GetMapping("/files/tree")
    public Object filesTree(@RequestParam(name = "baseDir", defaultValue = "${app.sync.base-dir:.}") String baseDir,
                            @RequestParam(name = "sqlDir", defaultValue = "${app.sync.sql-dir:sql}") String sqlDir) {
        log.info("api build file tree: baseDir={}, sqlDir={}", baseDir, sqlDir);
        return sqlQueryService.filesTree(baseDir, sqlDir);
    }

    @PostMapping("/files/{id}/execute")
    public ResponseEntity<?> execute(@PathVariable("id") int id,
                                     @RequestParam(name = "mode", defaultValue = "FILE") String mode,
                                     @RequestParam(name = "targetIds", required = false) List<String> targetIds) {
        log.info("api execute sql file: id={}, mode={}, targetIds={}", id, mode, targetIds);
        return ResponseEntity.ok(sqlExecutionService.execute(id, SqlExecutionService.TxMode.valueOf(mode), ExecutionRecord.ExecutedBy.MANUAL, targetIds));
    }

    @GetMapping("/records")
    public Object records(@RequestParam(name = "status", required = false) String status) {
        log.info("api list records: status={}", status);
        return sqlQueryService.listRecords(status);
    }

    @GetMapping("/records/view")
    public Object recordsView(@RequestParam(name = "status", required = false) String status) {
        log.info("api list record views: status={}", status);
        return sqlQueryService.recordsView(status);
    }

    @PostMapping("/sync/local")
    public ResponseEntity<?> syncLocal(@RequestParam(name = "baseDir", defaultValue = "${app.sync.base-dir:.}") String baseDir,
                                       @RequestParam(name = "sqlDir", defaultValue = "${app.sync.sql-dir:sql}") String sqlDir) {
        log.info("api sync local start: baseDir={}, sqlDir={}", baseDir, sqlDir);
        gitSyncUseCase.indexLocalSqlDir(baseDir, sqlDir);
        return ResponseEntity.ok(ApiResponseMaps.ok());
    }

    @GetMapping("/files/{id}/preview")
    public Object preview(@PathVariable int id) {
        log.info("api preview sql file: id={}", id);
        return sqlQueryService.preview(id);
    }
}

