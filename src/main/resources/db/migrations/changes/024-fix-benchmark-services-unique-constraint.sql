-- Fix benchmark_services unique constraint to be soft-delete compatible
-- Drop the existing UNIQUE constraint that doesn't account for soft-deletes
-- The constraint name varies by database engine, so we try known names with IF EXISTS
ALTER TABLE benchmark_services DROP CONSTRAINT IF EXISTS benchmark_services_benchmark_id_service_id_key;
ALTER TABLE benchmark_services DROP CONSTRAINT IF EXISTS constraint_3;

-- Create a partial unique index that only applies to non-deleted rows
-- This allows the same benchmark_id + service_id combination to exist multiple times
-- as long as only one row has deleted_at IS NULL
CREATE UNIQUE INDEX idx_benchmark_services_unique_active
ON benchmark_services (benchmark_id, service_id)
WHERE deleted_at IS NULL;
