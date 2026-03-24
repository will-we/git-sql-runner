package com.example.gitsqlrunner.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

public final class CompatFiles {
  private CompatFiles() {
  }

  public static byte[] readAllBytes(Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static String readUtf8(Path path) {
    return new String(readAllBytes(path), StandardCharsets.UTF_8);
  }

  public static String readUtf8(String path) {
    return readUtf8(Paths.get(path));
  }

  public static String sha256Hex(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] output = digest.digest(data);
      StringBuilder builder = new StringBuilder();
      for (byte value : output) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
