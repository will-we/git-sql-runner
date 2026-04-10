package io.will_we.project.gitsqlrunner.application;

import io.will_we.project.gitsqlrunner.domain.sql.SqlFileRepository;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Service
public class GitSyncUseCase {
  private static final Logger log = LoggerFactory.getLogger(GitSyncUseCase.class);
  private final SqlFileRepository sqlFileRepository;
  private final GitSqlFileSupport gitSqlFileSupport;

  public GitSyncUseCase(SqlFileRepository sqlFileRepository, GitSqlFileSupport gitSqlFileSupport) {
    this.sqlFileRepository = sqlFileRepository;
    this.gitSqlFileSupport = gitSqlFileSupport;
  }

  public void indexLocalSqlDir(String baseDir, String sqlDir) {
    try {
      File dir = gitSqlFileSupport.resolveSqlDir(baseDir, sqlDir);
      if (!dir.exists()) {
        log.warn("sync-local skipped: sql dir not exists, baseDir={}, sqlDir={}, resolved={}", baseDir, sqlDir, dir.getAbsolutePath());
        return;
      }
      Path basePath = gitSqlFileSupport.resolveBasePath(baseDir);
      log.info("sync-local start: baseDir={}, sqlDir={}, basePath={}, scanDir={}", baseDir, sqlDir, basePath, dir.getAbsolutePath());
      try (Git git = gitSqlFileSupport.openGit(basePath)) {
        Path workTree = git == null ? null : git.getRepository().getWorkTree().toPath().toAbsolutePath().normalize();
        log.info("sync-local git context: gitAvailable={}, workTree={}", git != null, workTree);
        List<Path> files = gitSqlFileSupport.collectSqlFiles(dir.toPath());
        int emptyCommitCount = 0;
        int outOfWorkTreeCount = 0;
        int fileLogMissCount = 0;
        int fallbackHeadCount = 0;
        for (Path path : files) {
          String filePath = path.toFile().getPath();
          String fileName = path.getFileName().toString();
          GitSqlFileSupport.CommitResolution resolution = gitSqlFileSupport.resolveCommitId(git, workTree, path.toAbsolutePath().normalize());
          String commitId = resolution.commitId();
          if (commitId.isEmpty()) {
            emptyCommitCount++;
          }
          if ("OUT_OF_WORK_TREE".equals(resolution.reason())) {
            outOfWorkTreeCount++;
          }
          if ("FILE_LOG_MISS_FALLBACK_HEAD".equals(resolution.reason())) {
            fileLogMissCount++;
          }
          if ("HEAD_FALLBACK".equals(resolution.source()) || "OUT_OF_WORK_TREE".equals(resolution.reason())) {
            fallbackHeadCount++;
          }
          if (commitId.isEmpty()) {
            log.warn("sync-local commit missing: file={}, reason={}, source={}", path.toAbsolutePath().normalize(), resolution.reason(), resolution.source());
          }
          sqlFileRepository.upsert(fileName, commitId, filePath, gitSqlFileSupport.checksum(path), Instant.now());
        }
        log.info("sync-local done: totalSqlFiles={}, emptyCommitCount={}, outOfWorkTreeCount={}, fileLogMissCount={}, fallbackHeadCount={}",
          files.size(), emptyCommitCount, outOfWorkTreeCount, fileLogMissCount, fallbackHeadCount);
      }
    } catch (Exception e) {
      log.error("sync-local failed: baseDir={}, sqlDir={}", baseDir, sqlDir, e);
      throw new RuntimeException(e);
    }
  }
}

