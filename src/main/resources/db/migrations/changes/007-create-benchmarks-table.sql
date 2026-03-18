CREATE TABLE benchmarks (
    id UUID PRIMARY KEY,
    environment_id UUID NOT NULL REFERENCES environments(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    k6_instructions TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_benchmarks_environment_id ON benchmarks(environment_id);
