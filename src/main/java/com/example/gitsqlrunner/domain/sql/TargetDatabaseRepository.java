package com.example.gitsqlrunner.domain.sql;

import java.util.List;
import java.util.Optional;

public interface TargetDatabaseRepository {
  List<TargetDatabase> findAll();
  Optional<TargetDatabase> findById(int id);
  Optional<TargetDatabase> findByTargetId(String targetId);
  int create(TargetDatabase targetDatabase);
  void update(TargetDatabase targetDatabase);
  void deleteById(int id);
}
