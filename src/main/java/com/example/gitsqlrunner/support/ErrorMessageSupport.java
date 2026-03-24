package com.example.gitsqlrunner.support;

import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

public final class ErrorMessageSupport {
  private ErrorMessageSupport() {
  }

  public static String safeMessage(Throwable throwable) {
    Throwable core = mostSpecificCause(throwable);
    if (core instanceof SQLException) {
      SQLException sqlException = (SQLException) core;
      String message = CompatText.isBlank(sqlException.getMessage()) ? sqlException.getClass().getSimpleName() : sqlException.getMessage();
      String sqlState = sqlException.getSQLState();
      int errorCode = sqlException.getErrorCode();
      if (CompatText.isBlank(sqlState) && errorCode == 0) {
        return message;
      }
      return message + " [SQLState=" + (CompatText.isBlank(sqlState) ? "UNKNOWN" : sqlState) + ", ErrorCode=" + errorCode + "]";
    }
    return CompatText.isBlank(core.getMessage()) ? core.getClass().getSimpleName() : core.getMessage();
  }

  public static String extractNearToken(String message) {
    if (message == null) {
      return null;
    }
    int nearIndex = message.indexOf("near \"");
    if (nearIndex < 0) {
      return null;
    }
    int start = nearIndex + 6;
    int end = message.indexOf('"', start);
    if (end <= start) {
      return null;
    }
    String token = message.substring(start, end).trim();
    return token.isEmpty() ? null : token;
  }

  public static String snippet(String text, int maxLength) {
    String normalized = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, maxLength) + "...";
  }

  private static Throwable mostSpecificCause(Throwable throwable) {
    if (throwable == null) {
      return new RuntimeException("UnknownException");
    }
    if (throwable instanceof DataAccessException) {
      DataAccessException dataAccessException = (DataAccessException) throwable;
      Throwable specific = dataAccessException.getMostSpecificCause();
      if (specific != null && specific != dataAccessException) {
        return rootCause(specific);
      }
    }
    return rootCause(throwable);
  }

  private static Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }
}
