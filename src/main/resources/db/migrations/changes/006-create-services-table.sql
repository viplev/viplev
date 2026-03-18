CREATE TABLE services (
    id UUID PRIMARY KEY,
    host_id UUID NOT NULL REFERENCES hosts(id),
    service_name VARCHAR(255) NOT NULL UNIQUE,
    image_sha VARCHAR(255),
    image_name VARCHAR(255),
    cpu_limit DOUBLE PRECISION,
    cpu_reservation DOUBLE PRECISION,
    memory_limit_bytes BIGINT,
    memory_reservation_bytes BIGINT
);
CREATE INDEX idx_services_host_id ON services(host_id);
