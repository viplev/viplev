ALTER TABLE hosts ADD CONSTRAINT uq_hosts_environment_name UNIQUE (environment_id, name);
