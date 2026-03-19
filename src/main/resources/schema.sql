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
  retry_count INTEGER DEFAULT 0,
  FOREIGN KEY(sql_file_id) REFERENCES sql_file(id)
);

