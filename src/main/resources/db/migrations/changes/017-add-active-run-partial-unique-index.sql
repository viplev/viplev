CREATE UNIQUE INDEX idx_benchmark_runs_active_unique
ON benchmark_runs (benchmark_id)
WHERE status IN ('PENDING_START', 'STARTED', 'PENDING_STOP');
