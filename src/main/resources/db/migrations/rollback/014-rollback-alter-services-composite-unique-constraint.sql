ALTER TABLE services DROP CONSTRAINT IF EXISTS uq_services_host_service_name;
ALTER TABLE services ADD CONSTRAINT services_service_name_key UNIQUE (service_name);
