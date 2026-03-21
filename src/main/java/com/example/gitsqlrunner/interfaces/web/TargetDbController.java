package com.example.gitsqlrunner.interfaces.web;

import com.example.gitsqlrunner.application.TargetDbConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/targets")
public class TargetDbController {
  private final TargetDbConfigService targetDbConfigService;

  public TargetDbController(TargetDbConfigService targetDbConfigService) {
    this.targetDbConfigService = targetDbConfigService;
  }

  @GetMapping
  public Object list() {
    return Map.of("items", targetDbConfigService.listAll());
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody TargetDbConfigService.TargetDbPayload payload) {
    try {
      return ResponseEntity.ok(targetDbConfigService.create(payload));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable int id, @RequestBody TargetDbConfigService.TargetDbPayload payload) {
    try {
      return ResponseEntity.ok(targetDbConfigService.update(id, payload));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable int id) {
    try {
      targetDbConfigService.delete(id);
      return ResponseEntity.ok(Map.of("ok", true));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
    }
  }

  @PostMapping("/test")
  public ResponseEntity<?> test(@RequestBody TargetDbConfigService.TargetDbPayload payload) {
    try {
      return ResponseEntity.ok(targetDbConfigService.testConnection(payload));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
    }
  }

  @PostMapping("/{id}/test")
  public ResponseEntity<?> testById(@PathVariable int id) {
    try {
      return ResponseEntity.ok(targetDbConfigService.testConnectionById(id));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
    }
  }
}
