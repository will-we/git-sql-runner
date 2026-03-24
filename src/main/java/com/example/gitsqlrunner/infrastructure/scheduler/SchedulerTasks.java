package com.example.gitsqlrunner.infrastructure.scheduler;

import com.example.gitsqlrunner.application.GitSyncUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SchedulerTasks {
  private final GitSyncUseCase gitSync;
  private final String defaultBaseDir;
  private final String defaultSqlDir;

  public SchedulerTasks(GitSyncUseCase gitSync,
                        @Value("${app.sync.base-dir:.}") String defaultBaseDir,
                        @Value("${app.sync.sql-dir:sql}") String defaultSqlDir) {
    this.gitSync = gitSync;
    this.defaultBaseDir = defaultBaseDir;
    this.defaultSqlDir = defaultSqlDir;
  }

  @Scheduled(cron = "0 */5 * * * *")
  public void periodicIndex() {
    long start = System.currentTimeMillis();
    log.info("scheduler sync start: baseDir={}, sqlDir={}", defaultBaseDir, defaultSqlDir);
    try {
      gitSync.indexLocalSqlDir(defaultBaseDir, defaultSqlDir);
      log.info("scheduler sync done: baseDir={}, sqlDir={}, costMs={}", defaultBaseDir, defaultSqlDir, System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.error("scheduler sync failed: baseDir={}, sqlDir={}, costMs={}", defaultBaseDir, defaultSqlDir, System.currentTimeMillis() - start, e);
      throw e;
    }
  }
}

