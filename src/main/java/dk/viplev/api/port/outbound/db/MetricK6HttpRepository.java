package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.MetricK6Http;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MetricK6HttpRepository extends JpaRepository<MetricK6Http, UUID> {
}
