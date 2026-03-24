package com.example.gitsqlrunner.infrastructure.persistence.jdbc;

import com.example.gitsqlrunner.domain.sql.SqlFile;
import com.example.gitsqlrunner.domain.sql.SqlFileRepository;
import com.example.gitsqlrunner.support.CompatTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("SqlDialectInspection")
@Repository
public class SqlFileRepositoryJdbc implements SqlFileRepository {
  private final JdbcTemplate jdbc;

  public SqlFileRepositoryJdbc(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void upsert(String fileName, String commitId, String filePath, String checksum, Instant lastSeenAt) {
    jdbc.update(
      "INSERT INTO sql_file(file_name, git_commit_id, file_path, checksum, last_seen_at) " +
        "VALUES (?,?,?,?,?) " +
        "ON CONFLICT(file_path, checksum) " +
        "DO UPDATE SET " +
        "last_seen_at=excluded.last_seen_at, " +
        "git_commit_id=CASE " +
        "WHEN excluded.git_commit_id IS NULL OR excluded.git_commit_id='' " +
        "THEN sql_file.git_commit_id " +
        "ELSE excluded.git_commit_id " +
        "END",
      fileName, commitId, filePath, checksum, lastSeenAt.toString()
    );
  }

  @Override
  public Optional<SqlFile> findById(int id) {
    List<SqlFile> list = jdbc.query("SELECT * FROM sql_file WHERE id=?", mapper, id);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public List<SqlFile> findAll(Boolean executed) {
    if (executed == null) {
      return jdbc.query("SELECT * FROM sql_file ORDER BY id DESC", mapper);
    }
    return jdbc.query("SELECT * FROM sql_file WHERE executed=? ORDER BY id DESC", mapper, executed ? 1 : 0);
  }

  @Override
  public void markExecuted(int id, SqlFile.Status status) {
    jdbc.update("UPDATE sql_file SET executed=?, last_status=? WHERE id=?",
      status == SqlFile.Status.SUCCESS ? 1 : 0, status.name(), id);
  }

  private static final RowMapper<SqlFile> mapper = new RowMapper<SqlFile>() {
    @Override
    public SqlFile mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new SqlFile(
        rs.getInt("id"),
        rs.getString("file_name"),
        rs.getString("git_commit_id"),
        rs.getString("file_path"),
        rs.getString("checksum"),
        CompatTime.parseInstant(rs.getString("last_seen_at")),
        rs.getInt("executed") == 1,
        SqlFile.Status.valueOf(rs.getString("last_status"))
      );
    }
  };
}

