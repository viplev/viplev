package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.BenchmarkService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BenchmarkServiceRepository extends JpaRepository<BenchmarkService, UUID> {

    List<BenchmarkService> findByBenchmarkIdAndDeletedAtIsNull(UUID benchmarkId);

    Optional<BenchmarkService> findByBenchmarkIdAndServiceId(UUID benchmarkId, UUID serviceId);
}
