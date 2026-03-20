-- Remove existing global unique constraint on service_name and replace with composite (host_id, service_name)
-- The constraint name varies by database engine, so we try known names with IF EXISTS
ALTER TABLE services DROP CONSTRAINT IF EXISTS services_service_name_key;
ALTER TABLE services DROP CONSTRAINT IF EXISTS constraint_7;
ALTER TABLE services ADD CONSTRAINT uq_services_host_service_name UNIQUE (host_id, service_name);
