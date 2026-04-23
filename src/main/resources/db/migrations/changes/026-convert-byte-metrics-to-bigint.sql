-- Convert byte metric columns from DOUBLE PRECISION to BIGINT
-- Use PostgreSQL-compatible ALTER COLUMN ... TYPE ... USING syntax for deterministic conversion

ALTER TABLE metric_resource_hosts ALTER COLUMN memory_usage_bytes TYPE BIGINT USING ROUND(memory_usage_bytes)::BIGINT;
ALTER TABLE metric_resource_hosts ALTER COLUMN memory_limit_bytes TYPE BIGINT USING ROUND(memory_limit_bytes)::BIGINT;
ALTER TABLE metric_resource_hosts ALTER COLUMN network_in_bytes TYPE BIGINT USING ROUND(network_in_bytes)::BIGINT;
ALTER TABLE metric_resource_hosts ALTER COLUMN network_out_bytes TYPE BIGINT USING ROUND(network_out_bytes)::BIGINT;
ALTER TABLE metric_resource_hosts ALTER COLUMN block_in_bytes TYPE BIGINT USING ROUND(block_in_bytes)::BIGINT;
ALTER TABLE metric_resource_hosts ALTER COLUMN block_out_bytes TYPE BIGINT USING ROUND(block_out_bytes)::BIGINT;

ALTER TABLE metric_resource_replicas ALTER COLUMN memory_usage_bytes TYPE BIGINT USING ROUND(memory_usage_bytes)::BIGINT;
ALTER TABLE metric_resource_replicas ALTER COLUMN memory_limit_bytes TYPE BIGINT USING ROUND(memory_limit_bytes)::BIGINT;
ALTER TABLE metric_resource_replicas ALTER COLUMN network_in_bytes TYPE BIGINT USING ROUND(network_in_bytes)::BIGINT;
ALTER TABLE metric_resource_replicas ALTER COLUMN network_out_bytes TYPE BIGINT USING ROUND(network_out_bytes)::BIGINT;
ALTER TABLE metric_resource_replicas ALTER COLUMN block_in_bytes TYPE BIGINT USING ROUND(block_in_bytes)::BIGINT;
ALTER TABLE metric_resource_replicas ALTER COLUMN block_out_bytes TYPE BIGINT USING ROUND(block_out_bytes)::BIGINT;
