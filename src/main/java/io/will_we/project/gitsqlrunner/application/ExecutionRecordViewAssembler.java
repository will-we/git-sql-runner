package io.will_we.project.gitsqlrunner.application;

import io.will_we.project.gitsqlrunner.domain.sql.ExecutionRecord;
import io.will_we.project.gitsqlrunner.domain.sql.SqlFile;
import io.will_we.project.gitsqlrunner.support.CompatTime;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class ExecutionRecordViewAssembler {
  public Map<String, Object> toView(ExecutionRecord record, SqlFile file) {
    Map<String, Object> view = new HashMap<String, Object>();
    view.put("id", record.getId());
    view.put("sqlFileId", record.getSqlFileId());
    view.put("sqlFileName", file == null ? null : file.getFileName());
    view.put("sqlFilePath", file == null ? null : file.getFilePath());
    view.put("status", record.getStatus().name());
    view.put("startTime", CompatTime.formatInstant(record.getStartTime()));
    view.put("endTime", CompatTime.formatInstant(record.getEndTime()));
    view.put("durationMs", durationMillis(record));
    view.put("errorMessage", record.getErrorMessage());
    view.put("executedBy", record.getExecutedBy().name());
    view.put("targetDbId", record.getTargetDbId());
    view.put("targetDbType", record.getTargetDbType());
    return view;
  }

  private Long durationMillis(ExecutionRecord record) {
    if (record.getStartTime() == null || record.getEndTime() == null) {
      return null;
    }
    return Duration.between(record.getStartTime(), record.getEndTime()).toMillis();
  }
}
