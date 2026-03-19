-- WARNING: This rollback deletes services with duplicate names across hosts,
-- keeping only the one with the largest id per service_name.
DELETE FROM services s1
  USING services s2
  WHERE s1.service_name = s2.service_name
    AND s1.host_id <> s2.host_id
    AND s1.id < s2.id;
ALTER TABLE services DROP CONSTRAINT IF EXISTS uq_services_host_service_name;
ALTER TABLE services ADD CONSTRAINT services_service_name_key UNIQUE (service_name);
