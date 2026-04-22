-- Convert byte metric columns from DOUBLE PRECISION to BIGINT
-- Using H2-compatible syntax that also works with PostgreSQL

ALTER TABLE metric_resource_hosts ALTER COLUMN memory_usage_bytes SET DATA TYPE BIGINT;
ALTER TABLE metric_resource_hosts ALTER COLUMN memory_limit_bytes SET DATA TYPE BIGINT;
ALTER TABLE metric_resource_hosts ALTER COLUMN network_in_bytes SET DATA TYPE BIGINT;
ALTER TABLE metric_resource_hosts ALTER COLUMN network_out_bytes SET DATA TYPE BIGINT;
ALTER TABLE metric_resource_hosts ALTER COLUMN block_in_bytes SET DATA TYPE BIGINT;
ALTER TABLE metric_resource_hosts ALTER COLUMN block_out_bytes SET DATA TYPE BIGINT;

ALTER TABLE metric_resource_replicas ALTER COLUMN memory_usage_bytes SET DATA TYPE BIGINT;
ALTER TABLE metric_resource_replicas ALTER COLUMN memory_limit_bytes SET DATA TYPE BIGINT;
ALTER TABLE metric_resource_replicas ALTER COLUMN network_in_bytes SET DATA TYPE BIGINT;
ALTER TABLE metric_resource_replicas ALTER COLUMN network_out_bytes SET DATA TYPE BIGINT;
ALTER TABLE metric_resource_replicas ALTER COLUMN block_in_bytes SET DATA TYPE BIGINT;
ALTER TABLE metric_resource_replicas ALTER COLUMN block_out_bytes SET DATA TYPE BIGINT;
