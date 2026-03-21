ALTER TABLE metric_resource_hosts DROP CONSTRAINT IF EXISTS metric_resource_hosts_run_id_fkey;
ALTER TABLE metric_resource_hosts ADD CONSTRAINT metric_resource_hosts_run_id_fkey FOREIGN KEY (run_id) REFERENCES benchmark_runs(id) ON DELETE CASCADE;

ALTER TABLE metric_resource_services DROP CONSTRAINT IF EXISTS metric_resource_services_run_id_fkey;
ALTER TABLE metric_resource_services ADD CONSTRAINT metric_resource_services_run_id_fkey FOREIGN KEY (run_id) REFERENCES benchmark_runs(id) ON DELETE CASCADE;

ALTER TABLE metric_k6_http DROP CONSTRAINT IF EXISTS metric_k6_http_run_id_fkey;
ALTER TABLE metric_k6_http ADD CONSTRAINT metric_k6_http_run_id_fkey FOREIGN KEY (run_id) REFERENCES benchmark_runs(id) ON DELETE CASCADE;

ALTER TABLE metric_k6_vus DROP CONSTRAINT IF EXISTS metric_k6_vus_run_id_fkey;
ALTER TABLE metric_k6_vus ADD CONSTRAINT metric_k6_vus_run_id_fkey FOREIGN KEY (run_id) REFERENCES benchmark_runs(id) ON DELETE CASCADE;
