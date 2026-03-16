CREATE TABLE metric_k6_vus (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES benchmark_runs(id),
    collected_at TIMESTAMP NOT NULL,
    vus INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_metric_k6_vus_run_id ON metric_k6_vus(run_id);
