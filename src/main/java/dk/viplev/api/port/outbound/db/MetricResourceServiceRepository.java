package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.MetricResourceService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MetricResourceServiceRepository extends JpaRepository<MetricResourceService, UUID> {
}
