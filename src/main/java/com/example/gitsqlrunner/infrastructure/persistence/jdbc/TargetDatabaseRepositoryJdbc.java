package com.example.gitsqlrunner.infrastructure.persistence.jdbc;

import com.example.gitsqlrunner.domain.sql.TargetDatabase;
import com.example.gitsqlrunner.domain.sql.TargetDatabaseRepository;
import com.example.gitsqlrunner.support.CompatTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("SqlDialectInspection")
@Repository
public class TargetDatabaseRepositoryJdbc implements TargetDatabaseRepository {
  private final JdbcTemplate jdbc;

  public TargetDatabaseRepositoryJdbc(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<TargetDatabase> findAll() {
    return jdbc.query("SELECT * FROM target_database ORDER BY id DESC", mapper);
  }

  @Override
  public Optional<TargetDatabase> findById(int id) {
    List<TargetDatabase> list = jdbc.query("SELECT * FROM target_database WHERE id=?", mapper, id);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public Optional<TargetDatabase> findByTargetId(String targetId) {
    List<TargetDatabase> list = jdbc.query("SELECT * FROM target_database WHERE target_id=?", mapper, targetId);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public int create(TargetDatabase targetDatabase) {
    jdbc.update(
      "INSERT INTO target_database(target_id, name, db_type, host, port, database_name, username, password, jdbc_params, enabled, created_at, updated_at) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
      targetDatabase.getTargetId(), targetDatabase.getName(), targetDatabase.getDbType(), targetDatabase.getHost(),
      targetDatabase.getPort(), targetDatabase.getDatabaseName(), targetDatabase.getUsername(), targetDatabase.getPassword(),
      targetDatabase.getJdbcParams(), targetDatabase.isEnabled() ? 1 : 0,
      targetDatabase.getCreatedAt().toString(), targetDatabase.getUpdatedAt().toString()
    );
    return jdbc.queryForObject("SELECT last_insert_rowid()", Integer.class);
  }

  @Override
  public void update(TargetDatabase targetDatabase) {
    jdbc.update(
      "UPDATE target_database SET target_id=?, name=?, db_type=?, host=?, port=?, database_name=?, username=?, password=?, jdbc_params=?, enabled=?, updated_at=? WHERE id=?",
      targetDatabase.getTargetId(), targetDatabase.getName(), targetDatabase.getDbType(), targetDatabase.getHost(),
      targetDatabase.getPort(), targetDatabase.getDatabaseName(), targetDatabase.getUsername(), targetDatabase.getPassword(),
      targetDatabase.getJdbcParams(), targetDatabase.isEnabled() ? 1 : 0, targetDatabase.getUpdatedAt().toString(), targetDatabase.getId()
    );
  }

  @Override
  public void deleteById(int id) {
    jdbc.update("DELETE FROM target_database WHERE id=?", id);
  }

  private static final RowMapper<TargetDatabase> mapper = (rs, rowNum) -> new TargetDatabase(
    rs.getInt("id"),
    rs.getString("target_id"),
    rs.getString("name"),
    rs.getString("db_type"),
    rs.getString("host"),
    rs.getInt("port"),
    rs.getString("database_name"),
    rs.getString("username"),
    rs.getString("password"),
    rs.getString("jdbc_params"),
    rs.getInt("enabled") == 1,
    CompatTime.parseInstant(rs.getString("created_at")),
    CompatTime.parseInstant(rs.getString("updated_at"))
  );
}
