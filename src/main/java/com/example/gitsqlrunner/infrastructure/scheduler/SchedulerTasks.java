package com.example.gitsqlrunner.infrastructure.scheduler;

import com.example.gitsqlrunner.application.GitSyncUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SchedulerTasks {
  private final GitSyncUseCase gitSync;

  public SchedulerTasks(GitSyncUseCase gitSync) {
    this.gitSync = gitSync;
  }

  @Scheduled(cron = "0 */5 * * * *")
  public void periodicIndex() {
    gitSync.indexLocalSqlDir(".", "sql");
  }
}

