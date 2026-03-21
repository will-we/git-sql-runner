package com.example.gitsqlrunner.domain.sql;

import java.time.Instant;

public class ExecutionRecord {
  private Integer id;
  private Integer sqlFileId;
  private Status status;
  private Instant startTime;
  private Instant endTime;
  private String errorMessage;
  private ExecutedBy executedBy;
  private int retryCount;
  private String targetDbId;
  private String targetDbType;

  public enum Status { SUCCESS, FAILED, RUNNING }
  public enum ExecutedBy { MANUAL, SCHEDULE }

  public ExecutionRecord(Integer id, Integer sqlFileId, Status status, Instant startTime, Instant endTime,
                         String errorMessage, ExecutedBy executedBy, int retryCount,
                         String targetDbId, String targetDbType) {
    this.id = id;
    this.sqlFileId = sqlFileId;
    this.status = status;
    this.startTime = startTime;
    this.endTime = endTime;
    this.errorMessage = errorMessage;
    this.executedBy = executedBy;
    this.retryCount = retryCount;
    this.targetDbId = targetDbId;
    this.targetDbType = targetDbType;
  }

  public Integer getId() { return id; }
  public Integer getSqlFileId() { return sqlFileId; }
  public Status getStatus() { return status; }
  public Instant getStartTime() { return startTime; }
  public Instant getEndTime() { return endTime; }
  public String getErrorMessage() { return errorMessage; }
  public ExecutedBy getExecutedBy() { return executedBy; }
  public int getRetryCount() { return retryCount; }
  public String getTargetDbId() { return targetDbId; }
  public String getTargetDbType() { return targetDbType; }
}

