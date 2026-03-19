package com.example.gitsqlrunner.infrastructure.persistence.jdbc;

import com.example.gitsqlrunner.domain.sql.ExecutionRecord;
import com.example.gitsqlrunner.domain.sql.ExecutionRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
public class ExecutionRecordRepositoryJdbc implements ExecutionRecordRepository {
  private final JdbcTemplate jdbc;

  public ExecutionRecordRepositoryJdbc(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public int createRunning(int sqlFileId, ExecutionRecord.ExecutedBy executedBy, Instant startTime) {
    jdbc.update("INSERT INTO execution_record(sql_file_id, status, start_time, executed_by) VALUES (?,?,?,?)",
      sqlFileId, ExecutionRecord.Status.RUNNING.name(), startTime.toString(), executedBy.name());
    return jdbc.queryForObject("SELECT last_insert_rowid()", Integer.class);
  }

  @Override
  public void markSuccess(int id, Instant endTime) {
    jdbc.update("UPDATE execution_record SET status=?, end_time=? WHERE id=?",
      ExecutionRecord.Status.SUCCESS.name(), endTime.toString(), id);
  }

  @Override
  public void markFailed(int id, Instant endTime, String error) {
    jdbc.update("UPDATE execution_record SET status=?, end_time=?, error_message=? WHERE id=?",
      ExecutionRecord.Status.FAILED.name(), endTime.toString(), error, id);
  }

  @Override
  public List<ExecutionRecord> findAll(String status) {
    if (status == null || status.isBlank()) {
      return jdbc.query("SELECT * FROM execution_record ORDER BY id DESC", mapper);
    }
    return jdbc.query("SELECT * FROM execution_record WHERE status=? ORDER BY id DESC", mapper, status);
  }

  private static final RowMapper<ExecutionRecord> mapper = new RowMapper<>() {
    @Override
    public ExecutionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new ExecutionRecord(
        rs.getInt("id"),
        rs.getInt("sql_file_id"),
        ExecutionRecord.Status.valueOf(rs.getString("status")),
        Instant.parse(rs.getString("start_time")),
        rs.getString("end_time") == null ? null : Instant.parse(rs.getString("end_time")),
        rs.getString("error_message"),
        ExecutionRecord.ExecutedBy.valueOf(rs.getString("executed_by")),
        rs.getInt("retry_count")
      );
    }
  };
}

