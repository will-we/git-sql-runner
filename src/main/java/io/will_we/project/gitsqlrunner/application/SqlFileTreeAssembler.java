package io.will_we.project.gitsqlrunner.application;

import io.will_we.project.gitsqlrunner.domain.sql.SqlFile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SqlFileTreeAssembler {
  public Map<String, Object> emptyTree(Path root) {
    Map<String, Object> result = new HashMap<String, Object>();
    result.put("root", root.toString());
    result.put("children", Collections.emptyList());
    return result;
  }

  public Map<String, Object> build(Path root, Map<String, SqlFile> indexed) {
    try {
      return buildNode(root, root, indexed);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, Object> buildNode(Path root, Path current, Map<String, SqlFile> indexed) throws Exception {
    Map<String, Object> node = new HashMap<String, Object>();
    node.put("name", current.equals(root) ? displayName(root) : current.getFileName().toString());
    node.put("path", current.toString());
    boolean directory = Files.isDirectory(current);
    node.put("type", directory ? "dir" : "file");
    if (!directory) {
      SqlFile sqlFile = indexed.get(current.normalize().toAbsolutePath().toString());
      node.put("id", sqlFile == null ? null : sqlFile.getId());
      node.put("executed", sqlFile != null && sqlFile.isExecuted());
      node.put("status", sqlFile == null || sqlFile.getLastStatus() == null ? null : sqlFile.getLastStatus().name());
      return node;
    }
    try (java.util.stream.Stream<Path> stream = Files.list(current)) {
      List<Path> children = stream
        .filter(path -> Files.isDirectory(path) || path.toString().toLowerCase().endsWith(".sql"))
        .sorted(Comparator
          .comparing((Path path) -> Files.isDirectory(path) ? 0 : 1)
          .thenComparing(path -> path.getFileName().toString().toLowerCase()))
        .collect(Collectors.toList());
      List<Map<String, Object>> childNodes = new ArrayList<Map<String, Object>>();
      for (Path child : children) {
        childNodes.add(buildNode(root, child, indexed));
      }
      node.put("children", childNodes);
      return node;
    }
  }

  private String displayName(Path path) {
    Path fileName = path.getFileName();
    return fileName == null ? path.toString() : fileName.toString();
  }
}
