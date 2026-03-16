CREATE TABLE hosts (
    id UUID PRIMARY KEY,
    environment_id UUID NOT NULL REFERENCES environments(id),
    name VARCHAR(255) NOT NULL,
    ip_address VARCHAR(255),
    os VARCHAR(255),
    os_version VARCHAR(255),
    cpu_model VARCHAR(255),
    cpu_cores INT,
    cpu_threads INT,
    ram_total_bytes BIGINT,
    ram_speed_mhz INT,
    ram_type VARCHAR(255)
);
CREATE INDEX idx_hosts_environment_id ON hosts(environment_id);
