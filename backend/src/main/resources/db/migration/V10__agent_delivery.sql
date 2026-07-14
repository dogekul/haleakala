ALTER TABLE agent_job ADD COLUMN dispatch_status VARCHAR(24) NOT NULL DEFAULT 'PENDING';
ALTER TABLE agent_job ADD COLUMN dispatch_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE agent_job ADD COLUMN dispatch_claimed_at TIMESTAMP(6) NULL;
ALTER TABLE agent_job ADD COLUMN next_dispatch_at TIMESTAMP(6) NULL;
ALTER TABLE agent_job ADD COLUMN last_polled_at TIMESTAMP(6) NULL;

UPDATE agent_job
SET dispatch_status = CASE
  WHEN external_job_id IS NOT NULL THEN 'SUBMITTED'
  WHEN status IN ('FAILED', 'TIMED_OUT', 'CANCELLED') THEN 'FAILED'
  ELSE 'PENDING'
END;

CREATE INDEX idx_agent_job_dispatch
  ON agent_job(dispatch_status, next_dispatch_at, created_at);
CREATE INDEX idx_agent_job_reconcile
  ON agent_job(status, last_polled_at, updated_at);
