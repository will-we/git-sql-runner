package io.will_we.project.gitsqlrunner.interfaces.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
    log.warn("api request invalid: method={}, uri={}, message={}",
      request.getMethod(), request.getRequestURI(), exception.getMessage());
    return ResponseEntity.badRequest().body(ApiResponseMaps.error(exception.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<?> handleIllegalState(IllegalStateException exception, HttpServletRequest request) {
    log.error("api request failed: method={}, uri={}, message={}",
      request.getMethod(), request.getRequestURI(), exception.getMessage(), exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponseMaps.error(exception.getMessage()));
  }
}
