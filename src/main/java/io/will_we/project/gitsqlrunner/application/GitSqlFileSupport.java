package io.will_we.project.gitsqlrunner.application;

import io.will_we.project.gitsqlrunner.support.CompatFiles;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GitSqlFileSupport {
  private static final Logger log = LoggerFactory.getLogger(GitSqlFileSupport.class);

  public Path resolveBasePath(String baseDir) {
    return Paths.get(baseDir).toAbsolutePath().normalize();
  }

  public File resolveSqlDir(String baseDir, String sqlDir) {
    return new File(baseDir, sqlDir);
  }

  public Git openGit(Path basePath) {
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

  public List<Path> collectSqlFiles(Path scanDir) {
    try {
      return Files.walk(scanDir)
        .filter(path -> path.toString().endsWith(".sql"))
        .collect(Collectors.toList());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public CommitResolution resolveCommitId(Git git, Path workTree, Path sqlFilePath) {
    if (git == null || workTree == null) {
      return new CommitResolution("", "NO_GIT_CONTEXT", "EMPTY");
    }
    try {
      String head = git.getRepository().resolve("HEAD") == null ? "" : git.getRepository().resolve("HEAD").name();
      if (!sqlFilePath.startsWith(workTree)) {
        return new CommitResolution(head, "OUT_OF_WORK_TREE", head.isEmpty() ? "EMPTY" : "HEAD_FALLBACK");
      }
      String relativePath = workTree.relativize(sqlFilePath).toString().replace(File.separatorChar, '/');
      Iterable<org.eclipse.jgit.revwalk.RevCommit> logs = git.log().addPath(relativePath).setMaxCount(1).call();
      for (org.eclipse.jgit.revwalk.RevCommit commit : logs) {
        return new CommitResolution(commit.getName(), "FILE_LOG_FOUND", "FILE_LOG");
      }
      return new CommitResolution(head, "FILE_LOG_MISS_FALLBACK_HEAD", head.isEmpty() ? "EMPTY" : "HEAD_FALLBACK");
    } catch (Exception e) {
      log.warn("sync-local resolve commit failed: absSqlPath={}, workTree={}", sqlFilePath, workTree, e);
      return new CommitResolution("", "RESOLVE_EXCEPTION", "EMPTY");
    }
  }

  public String checksum(Path sqlFilePath) {
    return CompatFiles.sha256Hex(CompatFiles.readAllBytes(sqlFilePath));
  }

  public static final class CommitResolution {
    private final String commitId;
    private final String reason;
    private final String source;

    public CommitResolution(String commitId, String reason, String source) {
      this.commitId = commitId;
      this.reason = reason;
      this.source = source;
    }

    public String commitId() {
      return commitId;
    }

    public String reason() {
      return reason;
    }

    public String source() {
      return source;
    }
  }
}
