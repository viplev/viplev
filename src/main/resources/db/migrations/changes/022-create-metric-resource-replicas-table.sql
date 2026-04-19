-- Create metric_resource_replicas table for replica-level metrics
-- Separate from metric_resource_services to avoid ambiguity and maintain backward compatibility
CREATE TABLE metric_resource_replicas (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES benchmark_runs(id) ON DELETE CASCADE,
    replica_id UUID NOT NULL REFERENCES service_replicas(id) ON DELETE CASCADE,
    collected_at TIMESTAMP NOT NULL,
    cpu_percentage DOUBLE PRECISION,
    memory_usage_bytes DOUBLE PRECISION,
    memory_limit_bytes DOUBLE PRECISION,
    network_in_bytes DOUBLE PRECISION,
    network_out_bytes DOUBLE PRECISION,
    block_in_bytes DOUBLE PRECISION,
    block_out_bytes DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_metric_resource_replicas_run_id ON metric_resource_replicas(run_id);
CREATE INDEX idx_metric_resource_replicas_replica_id ON metric_resource_replicas(replica_id);
