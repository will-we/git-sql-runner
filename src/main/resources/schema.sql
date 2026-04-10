CREATE TABLE IF NOT EXISTS sql_file(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  file_name TEXT NOT NULL,
  git_commit_id TEXT,
  file_path TEXT NOT NULL,
  checksum TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  executed INTEGER NOT NULL DEFAULT 0,
  last_status TEXT DEFAULT 'PENDING' CHECK(last_status IN ('SUCCESS','FAILED','PENDING')),
  UNIQUE(file_path, checksum)
);

CREATE TABLE IF NOT EXISTS execution_record(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sql_file_id INTEGER NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('SUCCESS','FAILED','RUNNING')),
  start_time TEXT NOT NULL,
  end_time TEXT,
  error_message TEXT,
  executed_by TEXT NOT NULL CHECK(executed_by IN ('MANUAL','SCHEDULE')),
  target_db_id TEXT,
  target_db_type TEXT,
  retry_count INTEGER DEFAULT 0,
  FOREIGN KEY(sql_file_id) REFERENCES sql_file(id)
);

CREATE INDEX IF NOT EXISTS idx_execution_record_file_target_time ON execution_record(sql_file_id, target_db_id, start_time DESC);

CREATE TABLE IF NOT EXISTS target_database(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  target_id TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  db_type TEXT NOT NULL CHECK(db_type IN ('mysql','postgresql')),
  driver_class_name TEXT,
  driver_jar_path TEXT,
  host TEXT NOT NULL,
  port INTEGER NOT NULL,
  database_name TEXT NOT NULL,
  username TEXT NOT NULL,
  password TEXT NOT NULL,
  jdbc_params TEXT,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
ALTER TABLE target_database ADD COLUMN IF NOT EXISTS driver_class_name TEXT;
ALTER TABLE target_database ADD COLUMN IF NOT EXISTS driver_jar_path TEXT;
CREATE INDEX IF NOT EXISTS idx_target_database_enabled ON target_database(enabled, target_id);

