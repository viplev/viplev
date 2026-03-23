package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.MetricResourceHost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MetricResourceHostRepository extends JpaRepository<MetricResourceHost, UUID> {

    List<MetricResourceHost> findByBenchmarkRunId(UUID runId);
}
