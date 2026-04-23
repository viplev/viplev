-- PostgreSQL-only partial unique index for active benchmark-service links
CREATE UNIQUE INDEX IF NOT EXISTS idx_benchmark_services_unique_active
ON benchmark_services (benchmark_id, service_id)
WHERE deleted_at IS NULL;
