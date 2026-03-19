package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.domain.sql.SqlFileRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Collectors;

@Service
public class GitSyncUseCase {
  private static final Logger log = LoggerFactory.getLogger(GitSyncUseCase.class);
  private final SqlFileRepository sqlFileRepository;

  public GitSyncUseCase(SqlFileRepository sqlFileRepository) {
    this.sqlFileRepository = sqlFileRepository;
  }

  public void indexLocalSqlDir(String baseDir, String sqlDir) {
    try {
      var dir = new File(baseDir, sqlDir);
      if (!dir.exists()) {
        log.warn("sync-local skipped: sql dir not exists, baseDir={}, sqlDir={}, resolved={}", baseDir, sqlDir, dir.getAbsolutePath());
        return;
      }
      Path basePath = Path.of(baseDir).toAbsolutePath().normalize();
      log.info("sync-local start: baseDir={}, sqlDir={}, basePath={}, scanDir={}", baseDir, sqlDir, basePath, dir.getAbsolutePath());
      try (Git git = openGit(basePath)) {
        Path workTree = git == null ? null : git.getRepository().getWorkTree().toPath().toAbsolutePath().normalize();
        log.info("sync-local git context: gitAvailable={}, workTree={}", git != null, workTree);
        var files = Files.walk(dir.toPath())
          .filter(p -> p.toString().endsWith(".sql"))
          .collect(Collectors.toList());
        int emptyCommitCount = 0;
        int outOfWorkTreeCount = 0;
        int fileLogMissCount = 0;
        int fallbackHeadCount = 0;
        for (var p : files) {
          String path = p.toFile().getPath();
          String name = p.getFileName().toString();
          var resolution = resolveCommitId(git, workTree, p.toAbsolutePath().normalize());
          String commitId = resolution.commitId();
          if (commitId.isEmpty()) emptyCommitCount++;
          if ("OUT_OF_WORK_TREE".equals(resolution.reason())) outOfWorkTreeCount++;
          if ("FILE_LOG_MISS_FALLBACK_HEAD".equals(resolution.reason())) fileLogMissCount++;
          if ("HEAD_FALLBACK".equals(resolution.source()) || "OUT_OF_WORK_TREE".equals(resolution.reason())) fallbackHeadCount++;
          if (commitId.isEmpty()) {
            log.warn("sync-local commit missing: file={}, reason={}, source={}", p.toAbsolutePath().normalize(), resolution.reason(), resolution.source());
          }
          sqlFileRepository.upsert(name, commitId, path, checksum(Files.readAllBytes(p)), Instant.now());
        }
        log.info("sync-local done: totalSqlFiles={}, emptyCommitCount={}, outOfWorkTreeCount={}, fileLogMissCount={}, fallbackHeadCount={}",
          files.size(), emptyCommitCount, outOfWorkTreeCount, fileLogMissCount, fallbackHeadCount);
      }
    } catch (Exception e) {
      log.error("sync-local failed: baseDir={}, sqlDir={}", baseDir, sqlDir, e);
      throw new RuntimeException(e);
    }
  }

  private static Git openGit(Path basePath) {
    try {
      FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(basePath.toFile());
      if (builder.getGitDir() == null) {
        log.warn("sync-local git not found from basePath={}", basePath);
        return null;
      }
      log.info("sync-local git found: gitDir={}, basePath={}", builder.getGitDir(), basePath);
      return new Git(builder.build());
    } catch (Exception e) {
      log.warn("sync-local open git failed: basePath={}", basePath, e);
      return null;
    }
  }

  private static CommitResolution resolveCommitId(Git git, Path workTree, Path absSqlPath) {
    if (git == null || workTree == null) return new CommitResolution("", "NO_GIT_CONTEXT", "EMPTY");
    try {
      String head = git.getRepository().resolve("HEAD") == null ? "" : git.getRepository().resolve("HEAD").name();
      if (!absSqlPath.startsWith(workTree)) {
        return new CommitResolution(head, "OUT_OF_WORK_TREE", head.isEmpty() ? "EMPTY" : "HEAD_FALLBACK");
      }
      String rel = workTree.relativize(absSqlPath).toString().replace(File.separatorChar, '/');
      var logs = git.log().addPath(rel).setMaxCount(1).call();
      for (var c : logs) return new CommitResolution(c.getName(), "FILE_LOG_FOUND", "FILE_LOG");
      return new CommitResolution(head, "FILE_LOG_MISS_FALLBACK_HEAD", head.isEmpty() ? "EMPTY" : "HEAD_FALLBACK");
    } catch (Exception e) {
      log.warn("sync-local resolve commit failed: absSqlPath={}, workTree={}", absSqlPath, workTree, e);
      return new CommitResolution("", "RESOLVE_EXCEPTION", "EMPTY");
    }
  }

  private record CommitResolution(String commitId, String reason, String source) {}

  private static String checksum(byte[] data) throws Exception {
    var md = java.security.MessageDigest.getInstance("SHA-256");
    md.update(data);
    var out = md.digest();
    var sb = new StringBuilder();
    for (byte b : out) sb.append(String.format("%02x", b));
    return sb.toString();
  }
}

