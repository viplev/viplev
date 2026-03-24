-- hosts → environments
ALTER TABLE hosts DROP CONSTRAINT IF EXISTS hosts_environment_id_fkey;
ALTER TABLE hosts ADD CONSTRAINT hosts_environment_id_fkey FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE;

-- benchmarks → environments
ALTER TABLE benchmarks DROP CONSTRAINT IF EXISTS benchmarks_environment_id_fkey;
ALTER TABLE benchmarks ADD CONSTRAINT benchmarks_environment_id_fkey FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE;

-- benchmark_runs → benchmarks
ALTER TABLE benchmark_runs DROP CONSTRAINT IF EXISTS benchmark_runs_benchmark_id_fkey;
ALTER TABLE benchmark_runs ADD CONSTRAINT benchmark_runs_benchmark_id_fkey FOREIGN KEY (benchmark_id) REFERENCES benchmarks(id) ON DELETE CASCADE;

-- services → hosts
ALTER TABLE services DROP CONSTRAINT IF EXISTS services_host_id_fkey;
ALTER TABLE services ADD CONSTRAINT services_host_id_fkey FOREIGN KEY (host_id) REFERENCES hosts(id) ON DELETE CASCADE;

-- metric_resource_hosts → hosts
ALTER TABLE metric_resource_hosts DROP CONSTRAINT IF EXISTS metric_resource_hosts_host_id_fkey;
ALTER TABLE metric_resource_hosts ADD CONSTRAINT metric_resource_hosts_host_id_fkey FOREIGN KEY (host_id) REFERENCES hosts(id) ON DELETE CASCADE;

-- metric_resource_services → services
ALTER TABLE metric_resource_services DROP CONSTRAINT IF EXISTS metric_resource_services_service_id_fkey;
ALTER TABLE metric_resource_services ADD CONSTRAINT metric_resource_services_service_id_fkey FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE;
