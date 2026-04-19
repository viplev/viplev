package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.MetricResourceReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MetricResourceReplicaRepository extends JpaRepository<MetricResourceReplica, UUID> {

    List<MetricResourceReplica> findByBenchmarkRunId(UUID benchmarkRunId);
}
