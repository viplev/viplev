ALTER TABLE hosts ADD COLUMN machine_id VARCHAR(255) NOT NULL;
ALTER TABLE hosts ADD CONSTRAINT uq_hosts_environment_machine UNIQUE (environment_id, machine_id);
