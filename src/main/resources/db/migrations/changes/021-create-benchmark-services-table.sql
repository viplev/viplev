-- Create benchmark_services table to track service-benchmark associations
-- This enables historical analysis of which services were involved in which benchmarks
CREATE TABLE benchmark_services (
    id UUID PRIMARY KEY,
    benchmark_id UUID NOT NULL REFERENCES benchmarks(id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    UNIQUE(benchmark_id, service_id)
);
CREATE INDEX idx_benchmark_services_benchmark_id ON benchmark_services(benchmark_id);
CREATE INDEX idx_benchmark_services_service_id ON benchmark_services(service_id);
