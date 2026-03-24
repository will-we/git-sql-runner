package com.example.gitsqlrunner.interfaces.web;

import com.example.gitsqlrunner.application.TargetDbConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/targets")
public class TargetDbController {
  private final TargetDbConfigService targetDbConfigService;

  public TargetDbController(TargetDbConfigService targetDbConfigService) {
    this.targetDbConfigService = targetDbConfigService;
  }

  @GetMapping
  public Object list() {
    log.info("api list target configs");
    return ApiResponseMaps.single("items", targetDbConfigService.listAll());
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody TargetDbConfigService.TargetDbPayload payload) {
    log.info("api create target config: targetId={}, dbType={}, host={}, port={}, databaseName={}",
      payload.targetId(), payload.dbType(), payload.host(), payload.port(), payload.databaseName());
    return ResponseEntity.ok(targetDbConfigService.create(payload));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable int id, @RequestBody TargetDbConfigService.TargetDbPayload payload) {
    log.info("api update target config: id={}, targetId={}, dbType={}, host={}, port={}, databaseName={}",
      id, payload.targetId(), payload.dbType(), payload.host(), payload.port(), payload.databaseName());
    return ResponseEntity.ok(targetDbConfigService.update(id, payload));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable int id) {
    log.info("api delete target config: id={}", id);
    targetDbConfigService.delete(id);
    return ResponseEntity.ok(ApiResponseMaps.ok());
  }

  @PostMapping("/test")
  public ResponseEntity<?> test(@RequestBody TargetDbConfigService.TargetDbPayload payload) {
    log.info("api test target config: targetId={}, dbType={}, host={}, port={}, databaseName={}",
      payload.targetId(), payload.dbType(), payload.host(), payload.port(), payload.databaseName());
    return ResponseEntity.ok(targetDbConfigService.testConnection(payload));
  }

  @PostMapping("/{id}/test")
  public ResponseEntity<?> testById(@PathVariable int id) {
    log.info("api test target config by id: id={}", id);
    return ResponseEntity.ok(targetDbConfigService.testConnectionById(id));
  }
}
