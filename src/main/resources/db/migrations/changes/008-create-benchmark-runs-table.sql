CREATE TABLE benchmark_runs (
    id UUID PRIMARY KEY,
    benchmark_id UUID NOT NULL REFERENCES benchmarks(id),
    started_by UUID NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL,
    status_reason TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_benchmark_runs_benchmark_id ON benchmark_runs(benchmark_id);
CREATE INDEX idx_benchmark_runs_started_by ON benchmark_runs(started_by);
