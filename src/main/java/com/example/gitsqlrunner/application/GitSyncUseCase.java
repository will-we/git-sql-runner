package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.domain.sql.SqlFileRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.stream.Collectors;

@Service
public class GitSyncUseCase {
  private final SqlFileRepository sqlFileRepository;

  public GitSyncUseCase(SqlFileRepository sqlFileRepository) {
    this.sqlFileRepository = sqlFileRepository;
  }

  public void indexLocalSqlDir(String baseDir, String sqlDir) {
    try {
      var dir = new File(baseDir, sqlDir);
      if (!dir.exists()) return;
      var files = Files.walk(dir.toPath())
        .filter(p -> p.toString().endsWith(".sql"))
        .collect(Collectors.toList());
      for (var p : files) {
        String path = p.toFile().getPath();
        String name = p.getFileName().toString();
        sqlFileRepository.upsert(name, "", path, checksum(Files.readAllBytes(p)), Instant.now());
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String checksum(byte[] data) throws Exception {
    var md = java.security.MessageDigest.getInstance("SHA-256");
    md.update(data);
    var out = md.digest();
    var sb = new StringBuilder();
    for (byte b : out) sb.append(String.format("%02x", b));
    return sb.toString();
  }
}

