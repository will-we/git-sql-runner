package com.example.gitsqlrunner.domain.sql;

import java.time.Instant;

public class SqlFile {
  private Integer id;
  private String fileName;
  private String gitCommitId;
  private String filePath;
  private String checksum;
  private Instant lastSeenAt;
  private boolean executed;
  private Status lastStatus;

  public enum Status { SUCCESS, FAILED, PENDING }

  public SqlFile(Integer id, String fileName, String gitCommitId, String filePath, String checksum,
                 Instant lastSeenAt, boolean executed, Status lastStatus) {
    this.id = id;
    this.fileName = fileName;
    this.gitCommitId = gitCommitId;
    this.filePath = filePath;
    this.checksum = checksum;
    this.lastSeenAt = lastSeenAt;
    this.executed = executed;
    this.lastStatus = lastStatus;
  }

  public Integer getId() { return id; }
  public String getFileName() { return fileName; }
  public String getGitCommitId() { return gitCommitId; }
  public String getFilePath() { return filePath; }
  public String getChecksum() { return checksum; }
  public Instant getLastSeenAt() { return lastSeenAt; }
  public boolean isExecuted() { return executed; }
  public Status getLastStatus() { return lastStatus; }

  public void markExecuted(Status status) {
    this.executed = status == Status.SUCCESS;
    this.lastStatus = status;
  }
}

