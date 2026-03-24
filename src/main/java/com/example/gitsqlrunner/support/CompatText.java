package com.example.gitsqlrunner.support;

public final class CompatText {
  private CompatText() {
  }

  public static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public static String requireText(String value, String field) {
    String trimmed = trimToNull(value);
    if (trimmed == null) {
      throw new IllegalArgumentException(field + " required");
    }
    return trimmed;
  }

  public static String defaultString(String value) {
    return value == null ? "" : value;
  }
}
