package com.example.gitsqlrunner.interfaces.web;

import com.example.gitsqlrunner.application.GitSyncUseCase;
import com.example.gitsqlrunner.application.SqlExecutionService;
import com.example.gitsqlrunner.domain.sql.ExecutionRecord;
import com.example.gitsqlrunner.domain.sql.ExecutionRecordRepository;
import com.example.gitsqlrunner.domain.sql.SqlFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SqlApiController {
    /**
     * SQL 脚本与执行记录相关的 HTTP API。
     * <p>
     * 统一前缀：/api
     * 返回内容：均为 JSON；异常由全局异常处理返回标准错误结构（若未捕获则为 Spring 默认错误结构）。
     */
    private final SqlFileRepository fileRepo;
    private final ExecutionRecordRepository recordRepo;
    private final SqlExecutionService execService;
    private final GitSyncUseCase gitSync;
    private final String defaultBaseDir;
    private final String defaultSqlDir;

    public SqlApiController(SqlFileRepository fileRepo,
                            ExecutionRecordRepository recordRepo,
                            SqlExecutionService execService,
                            GitSyncUseCase gitSync,
                            @Value("${app.sync.base-dir:.}") String defaultBaseDir,
                            @Value("${app.sync.sql-dir:sql}") String defaultSqlDir) {
        this.fileRepo = fileRepo;
        this.recordRepo = recordRepo;
        this.execService = execService;
        this.gitSync = gitSync;
        this.defaultBaseDir = defaultBaseDir;
        this.defaultSqlDir = defaultSqlDir;
    }

    /**
     * 获取 SQL 文件列表。
     * <p>
     * GET /api/files
     *
     * @param executed 可选；true 仅返回已执行，false 仅返回未执行；不传返回全部
     * @return 数组：[{ id, fileName, gitCommitId, filePath, checksum, lastSeenAt, executed, lastStatus }]
     */
    @GetMapping("/files")
    public Object files(@RequestParam(name = "executed", required = false) Boolean executed) {
        return fileRepo.findAll(executed);
    }

    @GetMapping("/settings/sync-dir")
    public Object syncDirSettings() {
        return Map.of("baseDir", defaultBaseDir, "sqlDir", defaultSqlDir);
    }

    @GetMapping("/files/tree")
    public Object filesTree(@RequestParam(name = "baseDir", defaultValue = "${app.sync.base-dir:.}") String baseDir,
                            @RequestParam(name = "sqlDir", defaultValue = "${app.sync.sql-dir:sql}") String sqlDir) {
        try {
            Path root = Path.of(baseDir).resolve(sqlDir).normalize().toAbsolutePath();
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                return Map.of("root", root.toString(), "children", List.of());
            }
            Map<String, com.example.gitsqlrunner.domain.sql.SqlFile> indexed = fileRepo.findAll(null).stream()
                    .collect(Collectors.toMap(
                            f -> Path.of(f.getFilePath()).normalize().toAbsolutePath().toString(),
                            f -> f,
                            (a, b) -> a
                    ));
            Map<String, Object> node = buildTreeNode(root, root, indexed);
            return node;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行指定 SQL 文件。
     * <p>
     * POST /api/files/{id}/execute
     *
     * @param id   路径参数；sql_file 表主键
     * @param mode 可选；事务模式：FILE（整文件事务，默认）或 STATEMENT（逐语句提交）
     * @return {"ok": true} 表示已触发执行并记录结果；失败时返回 4xx/5xx 错误
     */
    @PostMapping("/files/{id}/execute")
    public ResponseEntity<?> execute(@PathVariable("id") int id,
                                     @RequestParam(name = "mode", defaultValue = "FILE") String mode) {
        execService.execute(id, SqlExecutionService.TxMode.valueOf(mode), ExecutionRecord.ExecutedBy.MANUAL);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * 获取执行记录列表。
     * <p>
     * GET /api/records
     *
     * @param status 可选；过滤状态：SUCCESS / FAILED / RUNNING；不传返回全部
     * @return 数组：[{ id, sqlFileId, status, startTime, endTime, errorMessage, executedBy, retryCount }]
     */
    @GetMapping("/records")
    public Object records(@RequestParam(name = "status", required = false) String status) {
        return recordRepo.findAll(status);
    }

    @GetMapping("/records/view")
    public Object recordsView(@RequestParam(name = "status", required = false) String status) {
        List<ExecutionRecord> records = recordRepo.findAll(status);
        var fileMap = new HashMap<Integer, Object>();
        fileRepo.findAll(null).forEach(f -> fileMap.put(f.getId(), f));
        return records.stream().map(r -> {
            var f = (com.example.gitsqlrunner.domain.sql.SqlFile) fileMap.get(r.getSqlFileId());
            Long durationMs = null;
            if (r.getStartTime() != null && r.getEndTime() != null) {
                durationMs = Duration.between(r.getStartTime(), r.getEndTime()).toMillis();
            }
            Map<String, Object> view = new HashMap<>();
            view.put("id", r.getId());
            view.put("sqlFileId", r.getSqlFileId());
            view.put("sqlFileName", f == null ? null : f.getFileName());
            view.put("sqlFilePath", f == null ? null : f.getFilePath());
            view.put("status", r.getStatus().name());
            view.put("startTime", r.getStartTime() == null ? null : r.getStartTime().toString());
            view.put("endTime", r.getEndTime() == null ? null : r.getEndTime().toString());
            view.put("durationMs", durationMs);
            view.put("errorMessage", r.getErrorMessage());
            view.put("executedBy", r.getExecutedBy().name());
            return view;
        }).toList();
    }

    /**
     * 索引本地目录下的 SQL 文件。
     * <p>
     * POST /api/sync/local
     *
     * @param baseDir 根目录（默认 . 为应用工作目录）
     * @param sqlDir  相对 baseDir 的 SQL 子目录（默认 "sql"）
     * @return {"ok": true} 表示已完成目录扫描并 upsert 至 sql_file 表
     */
    @PostMapping("/sync/local")
    public ResponseEntity<?> syncLocal(@RequestParam(name = "baseDir", defaultValue = "${app.sync.base-dir:.}") String baseDir,
                                       @RequestParam(name = "sqlDir", defaultValue = "${app.sync.sql-dir:sql}") String sqlDir) {
        gitSync.indexLocalSqlDir(baseDir, sqlDir);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * 预览 SQL 文件内容（只读）。
     * <p>
     * GET /api/files/{id}/preview
     *
     * @param id 路径参数；sql_file 表主键
     * @return { id, fileName, filePath, gitCommitId, content }；若未找到返回 { "error": "Not Found" }
     */
    @GetMapping("/files/{id}/preview")
    public Object preview(@PathVariable int id) {
        return fileRepo.findById(id)
                .map(f -> {
                    try {
                        String content = Files.readString(Path.of(f.getFilePath()));
                        Map<String, Object> result = new HashMap<>();
                        result.put("id", f.getId());
                        result.put("fileName", f.getFileName());
                        result.put("filePath", f.getFilePath());
                        result.put("gitCommitId", f.getGitCommitId());
                        result.put("content", content);
                        result.put("syntaxCheck", execService.checkSyntax(content));
                        return result;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElseGet(() -> Map.of("error", "Not Found"));
    }

    private Map<String, Object> buildTreeNode(Path root,
                                              Path current,
                                              Map<String, com.example.gitsqlrunner.domain.sql.SqlFile> indexed) throws Exception {
        Map<String, Object> node = new HashMap<>();
        node.put("name", current.equals(root) ? root.getFileName().toString() : current.getFileName().toString());
        node.put("path", current.toString());
        boolean isDir = Files.isDirectory(current);
        node.put("type", isDir ? "dir" : "file");
        if (!isDir) {
            String key = current.normalize().toAbsolutePath().toString();
            var f = indexed.get(key);
            node.put("id", f == null ? null : f.getId());
            node.put("executed", f != null && f.isExecuted());
            node.put("status", f == null || f.getLastStatus() == null ? null : f.getLastStatus().name());
            return node;
        }
        try (var stream = Files.list(current)) {
            List<Path> children = stream
                    .filter(p -> Files.isDirectory(p) || p.toString().toLowerCase().endsWith(".sql"))
                    .sorted(Comparator
                            .comparing((Path p) -> Files.isDirectory(p) ? 0 : 1)
                            .thenComparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
            List<Map<String, Object>> childNodes = new java.util.ArrayList<>();
            for (Path child : children) {
                childNodes.add(buildTreeNode(root, child, indexed));
            }
            node.put("children", childNodes);
            return node;
        }
    }
}

