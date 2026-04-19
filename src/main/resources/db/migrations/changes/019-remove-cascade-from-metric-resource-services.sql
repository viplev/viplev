-- Remove ON DELETE CASCADE from metric_resource_services.service_id FK
-- Replace with ON DELETE RESTRICT to prevent accidental deletion of historical metrics
-- This is critical for soft-delete: services with metrics cannot be hard-deleted
ALTER TABLE metric_resource_services DROP CONSTRAINT IF EXISTS metric_resource_services_service_id_fkey;
ALTER TABLE metric_resource_services ADD CONSTRAINT metric_resource_services_service_id_fkey FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT;
