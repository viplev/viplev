package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.BenchmarkRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, UUID> {

    List<BenchmarkRun> findByBenchmarkId(UUID benchmarkId);

    Optional<BenchmarkRun> findByIdAndBenchmarkId(UUID id, UUID benchmarkId);
}
