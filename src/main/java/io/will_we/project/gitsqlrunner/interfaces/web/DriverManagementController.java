package io.will_we.project.gitsqlrunner.interfaces.web;

import io.will_we.project.gitsqlrunner.application.DriverManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/drivers")
public class DriverManagementController {
  private final DriverManagementService driverManagementService;

  public DriverManagementController(DriverManagementService driverManagementService) {
    this.driverManagementService = driverManagementService;
  }

  @GetMapping
  public Object list() {
    log.info("api list driver files");
    return ApiResponseMaps.single("items", driverManagementService.listDrivers());
  }

  @PostMapping("/upload")
  public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
    log.info("api upload driver file: fileName={}, size={}", file == null ? null : file.getOriginalFilename(), file == null ? null : file.getSize());
    return ResponseEntity.ok(driverManagementService.saveDriver(file));
  }

  @PostMapping("/resolve")
  public ResponseEntity<?> resolve(@RequestParam("path") String path) {
    log.info("api resolve driver path: path={}", path);
    Map<String, Object> result = driverManagementService.resolveDriverPath(path);
    return ResponseEntity.ok(result);
  }
}
