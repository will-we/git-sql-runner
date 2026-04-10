package io.will_we.project.gitsqlrunner.application;

import io.will_we.project.gitsqlrunner.support.CompatText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DriverManagementService {
  private final Path storageDir;

  public DriverManagementService(@Value("${app.driver.storage-dir:drivers}") String storageDir) {
    this.storageDir = Paths.get(storageDir).toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.storageDir);
    } catch (Exception e) {
      throw new IllegalStateException("init driver storage dir failed: " + this.storageDir, e);
    }
  }

  public List<Map<String, Object>> listDrivers() {
    try {
      List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
      Files.list(storageDir)
        .filter(Files::isRegularFile)
        .forEach(path -> result.add(toView(path)));
      log.info("list drivers: storageDir={}, count={}", storageDir, result.size());
      return result;
    } catch (Exception e) {
      throw new IllegalStateException("list drivers failed: " + storageDir, e);
    }
  }

  public Map<String, Object> saveDriver(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("driver file required");
    }
    String originalName = CompatText.trimToNull(file.getOriginalFilename());
    if (originalName == null) {
      throw new IllegalArgumentException("driver file name required");
    }
    String safeName = originalName.replace("\\", "/");
    int idx = safeName.lastIndexOf('/');
    if (idx >= 0) {
      safeName = safeName.substring(idx + 1);
    }
    if (CompatText.trimToNull(safeName) == null) {
      throw new IllegalArgumentException("driver file name invalid");
    }
    Path targetPath = uniquePath(storageDir.resolve(safeName).normalize());
    try {
      file.transferTo(targetPath.toFile());
      log.info("driver file uploaded: fileName={}, path={}, size={}", targetPath.getFileName(), targetPath, Files.size(targetPath));
      return toView(targetPath);
    } catch (Exception e) {
      throw new IllegalStateException("save driver file failed: " + targetPath, e);
    }
  }

  public Map<String, Object> resolveDriverPath(String driverJarPath) {
    String path = CompatText.requireText(driverJarPath, "driverJarPath");
    Path targetPath = Paths.get(path).toAbsolutePath().normalize();
    File file = targetPath.toFile();
    if (!file.exists() || !file.isFile()) {
      throw new IllegalArgumentException("driver jar not found: " + targetPath);
    }
    Map<String, Object> result = new HashMap<String, Object>();
    result.put("ok", true);
    result.put("path", targetPath.toString());
    result.put("fileName", targetPath.getFileName() == null ? targetPath.toString() : targetPath.getFileName().toString());
    log.info("driver path resolved: path={}", targetPath);
    return result;
  }

  private Map<String, Object> toView(Path path) {
    try {
      Map<String, Object> result = new HashMap<String, Object>();
      result.put("fileName", path.getFileName() == null ? path.toString() : path.getFileName().toString());
      result.put("path", path.toString());
      result.put("size", Files.size(path));
      result.put("lastModifiedAt", Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()).toString());
      return result;
    } catch (Exception e) {
      throw new IllegalStateException("read driver file metadata failed: " + path, e);
    }
  }

  private Path uniquePath(Path path) {
    if (!Files.exists(path)) {
      return path;
    }
    String name = path.getFileName() == null ? "driver.jar" : path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    String ext = dot > 0 ? name.substring(dot) : "";
    String uniqueName = base + "-" + System.currentTimeMillis() + ext;
    return path.getParent() == null ? Paths.get(uniqueName) : path.getParent().resolve(uniqueName);
  }
}
