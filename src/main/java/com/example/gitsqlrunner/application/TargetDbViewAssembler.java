   package com.example.gitsqlrunner.application;

import com.example.gitsqlrunner.domain.sql.TargetDatabase;
import com.example.gitsqlrunner.support.CompatText;
import com.example.gitsqlrunner.support.CompatTime;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TargetDbViewAssembler {
  public Map<String, Object> toView(TargetDatabase targetDatabase) {
    Map<String, Object> view = new HashMap<String, Object>();
    view.put("id", targetDatabase.getId());
    view.put("targetId", targetDatabase.getTargetId());
    view.put("name", targetDatabase.getName());
    view.put("dbType", targetDatabase.getDbType());
    view.put("host", targetDatabase.getHost());
    view.put("port", targetDatabase.getPort());
    view.put("databaseName", targetDatabase.getDatabaseName());
    view.put("username", targetDatabase.getUsername());
    view.put("jdbcParams", targetDatabase.getJdbcParams());
    view.put("enabled", targetDatabase.isEnabled());
    view.put("createdAt", CompatTime.formatInstant(targetDatabase.getCreatedAt()));
    view.put("updatedAt", CompatTime.formatInstant(targetDatabase.getUpdatedAt()));
    view.put("passwordSet", CompatText.trimToNull(targetDatabase.getPassword()) != null);
    return view;
  }
}
