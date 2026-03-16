CREATE TABLE metric_resource_hosts (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES benchmark_runs(id),
    host_id UUID NOT NULL REFERENCES hosts(id),
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
CREATE INDEX idx_metric_resource_hosts_run_id ON metric_resource_hosts(run_id);
CREATE INDEX idx_metric_resource_hosts_host_id ON metric_resource_hosts(host_id);
