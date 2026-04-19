-- Add soft delete support to services table
ALTER TABLE services ADD COLUMN deleted_at TIMESTAMP NULL;
CREATE INDEX idx_services_host_id_deleted_at ON services(host_id, deleted_at);

-- Create service_replicas table for tracking individual container instances
CREATE TABLE service_replicas (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    container_id VARCHAR(255) NOT NULL,
    started_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,
    UNIQUE(service_id, container_id)
);
CREATE INDEX idx_service_replicas_service_id ON service_replicas(service_id);
CREATE INDEX idx_service_replicas_deleted_at ON service_replicas(deleted_at);
