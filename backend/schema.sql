-- Already applied to D1 "catatku". Kept for reference / re-create.
CREATE TABLE IF NOT EXISTS notes (
  id TEXT PRIMARY KEY,
  text TEXT NOT NULL,
  tags TEXT NOT NULL DEFAULT '',
  pinned INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0,
  srv_ts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_notes_updated ON notes(updated_at);
CREATE INDEX IF NOT EXISTS idx_notes_srv ON notes(srv_ts);
