package com.example.gitsqlrunner.domain.sql;

import java.time.Instant;
import java.util.List;

public interface ExecutionRecordRepository {
  int createRunning(int sqlFileId, String targetDbId, String targetDbType, ExecutionRecord.ExecutedBy executedBy, Instant startTime);
  void markSuccess(int id, Instant endTime);
  void markFailed(int id, Instant endTime, String error);
  List<ExecutionRecord> findAll(String status);
}

