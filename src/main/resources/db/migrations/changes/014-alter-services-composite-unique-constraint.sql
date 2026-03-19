ALTER TABLE services DROP CONSTRAINT IF EXISTS services_service_name_key;
ALTER TABLE services ADD CONSTRAINT uq_services_host_service_name UNIQUE (host_id, service_name);
