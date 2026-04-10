package io.will_we.project.gitsqlrunner.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class CompatTime {
  private CompatTime() {
  }

  public static Instant parseInstant(String raw) {
    String value = CompatText.trimToNull(raw);
    if (value == null) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (Exception ignored) {
      return LocalDateTime.parse(value.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
    }
  }

  public static String formatInstant(Instant value) {
    return value == null ? null : value.toString();
  }
}
