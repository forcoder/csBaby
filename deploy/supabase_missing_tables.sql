-- BUG-R11 修复: 在 Supabase 上补全主 API 同步需要的表
-- 风格: 跟现有表一致 (tenant_id NOT NULL + sync_version + deleted)

CREATE TABLE IF NOT EXISTS feedback (
  id bigint PRIMARY KEY,
  tenant_id text NOT NULL,
  reply_history_id bigint,
  action text,
  modified_text text,
  rating integer,
  comment text,
  created_at bigint,
  sync_version bigint,
  deleted boolean DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_feedback_tenant ON feedback(tenant_id);

CREATE TABLE IF NOT EXISTS optimization_metrics (
  id bigint PRIMARY KEY,
  tenant_id text NOT NULL,
  date text,
  total_generated integer DEFAULT 0,
  total_accepted integer DEFAULT 0,
  total_modified integer DEFAULT 0,
  total_rejected integer DEFAULT 0,
  avg_confidence real DEFAULT 0,
  avg_response_time_ms integer DEFAULT 0,
  sync_version bigint,
  deleted boolean DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_metrics_tenant ON optimization_metrics(tenant_id);
