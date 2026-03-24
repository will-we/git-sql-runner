package com.example.gitsqlrunner.infrastructure.persistence.jdbc;

import com.example.gitsqlrunner.domain.sql.ExecutionRecord;
import com.example.gitsqlrunner.domain.sql.ExecutionRecordRepository;
import com.example.gitsqlrunner.support.CompatText;
import com.example.gitsqlrunner.support.CompatTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@SuppressWarnings("SqlDialectInspection")
@Repository
public class ExecutionRecordRepositoryJdbc implements ExecutionRecordRepository {
  private final JdbcTemplate jdbc;

  public ExecutionRecordRepositoryJdbc(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public int createRunning(int sqlFileId, String targetDbId, String targetDbType, ExecutionRecord.ExecutedBy executedBy, Instant startTime) {
    jdbc.update("INSERT INTO execution_record(sql_file_id, status, start_time, executed_by, target_db_id, target_db_type) VALUES (?,?,?,?,?,?)",
      sqlFileId, ExecutionRecord.Status.RUNNING.name(), startTime.toString(), executedBy.name(), targetDbId, targetDbType);
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
    if (CompatText.isBlank(status)) {
      return jdbc.query("SELECT * FROM execution_record ORDER BY id DESC", mapper);
    }
    return jdbc.query("SELECT * FROM execution_record WHERE status=? ORDER BY id DESC", mapper, status);
  }

  private static final RowMapper<ExecutionRecord> mapper = new RowMapper<ExecutionRecord>() {
    @Override
    public ExecutionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new ExecutionRecord(
        rs.getInt("id"),
        rs.getInt("sql_file_id"),
        ExecutionRecord.Status.valueOf(rs.getString("status")),
        CompatTime.parseInstant(rs.getString("start_time")),
        CompatTime.parseInstant(rs.getString("end_time")),
        rs.getString("error_message"),
        ExecutionRecord.ExecutedBy.valueOf(rs.getString("executed_by")),
        rs.getInt("retry_count"),
        rs.getString("target_db_id"),
        rs.getString("target_db_type")
      );
    }
  };
}

