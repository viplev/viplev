-- Enforce uniqueness for active benchmark-service scopes only
ALTER TABLE benchmark_services DROP CONSTRAINT IF EXISTS benchmark_services_benchmark_id_service_id_key;
ALTER TABLE benchmark_services DROP CONSTRAINT IF EXISTS constraint_3;
ALTER TABLE benchmark_services DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_AB2;
ALTER TABLE benchmark_services DROP CONSTRAINT IF EXISTS CONSTRAINT_AB2D;
