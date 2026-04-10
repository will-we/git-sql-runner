package io.will_we.project.gitsqlrunner.domain.sql;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SqlFileRepository {
  void upsert(String fileName, String commitId, String filePath, String checksum, Instant lastSeenAt);
  Optional<SqlFile> findById(int id);
  List<SqlFile> findAll(Boolean executed);
  void markExecuted(int id, SqlFile.Status status);
}

