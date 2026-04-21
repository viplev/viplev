-- Refactor topology from host-centric to environment-centric
-- Environment -> Service -> Replica (+ Host attachment)

-- Drop dependent constraints first
ALTER TABLE benchmark_services DROP CONSTRAINT IF EXISTS fk_benchmark_services_service_id;
ALTER TABLE metric_resource_replicas DROP CONSTRAINT IF EXISTS fk_metric_resource_replicas_replica_id;

-- Drop existing indexes
DROP INDEX IF EXISTS idx_services_host_id_deleted_at;
DROP INDEX IF EXISTS idx_service_replicas_service_id;
DROP INDEX IF EXISTS idx_service_replicas_deleted_at;

-- Delete all data (destructive - data loss acceptable per issue)
-- Using DELETE instead of TRUNCATE for H2 compatibility (H2 cannot truncate tables with FK references)
DELETE FROM metric_resource_replicas;
DELETE FROM benchmark_services;
DELETE FROM service_replicas;
DELETE FROM services;

-- Rebuild services table with environment_id
ALTER TABLE services DROP CONSTRAINT IF EXISTS services_host_id_service_name_key;
ALTER TABLE services DROP CONSTRAINT IF EXISTS uq_services_host_service_name;
ALTER TABLE services DROP COLUMN host_id;
ALTER TABLE services ADD COLUMN environment_id UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE;
ALTER TABLE services ADD CONSTRAINT services_environment_id_service_name_key UNIQUE (environment_id, service_name);

-- Add host_id and container_name to service_replicas
ALTER TABLE service_replicas ADD COLUMN host_id UUID NOT NULL REFERENCES hosts(id) ON DELETE CASCADE;
ALTER TABLE service_replicas ADD COLUMN container_name VARCHAR(255) NOT NULL;

-- Add global uniqueness constraint for container_id (Docker container IDs are globally unique)
ALTER TABLE service_replicas ADD CONSTRAINT service_replicas_container_id_key UNIQUE (container_id);

-- Recreate indexes
CREATE INDEX idx_services_environment_id_deleted_at ON services(environment_id, deleted_at);
CREATE INDEX idx_service_replicas_service_id ON service_replicas(service_id);
CREATE INDEX idx_service_replicas_host_id ON service_replicas(host_id);
CREATE INDEX idx_service_replicas_deleted_at ON service_replicas(deleted_at);

-- Recreate foreign key constraints
ALTER TABLE benchmark_services ADD CONSTRAINT fk_benchmark_services_service_id FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE;
ALTER TABLE metric_resource_replicas ADD CONSTRAINT fk_metric_resource_replicas_replica_id FOREIGN KEY (replica_id) REFERENCES service_replicas(id) ON DELETE CASCADE;
