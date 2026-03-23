package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.MetricK6Vus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MetricK6VusRepository extends JpaRepository<MetricK6Vus, UUID> {

    List<MetricK6Vus> findByBenchmarkRunId(UUID runId);
}
