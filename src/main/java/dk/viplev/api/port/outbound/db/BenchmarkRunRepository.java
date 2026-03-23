package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, UUID> {

    List<BenchmarkRun> findByBenchmarkId(UUID benchmarkId);

    Optional<BenchmarkRun> findByIdAndBenchmarkId(UUID id, UUID benchmarkId);

    boolean existsByBenchmarkIdAndStatusIn(UUID benchmarkId, Collection<BenchmarkRunStatus> statuses);

    @Query("SELECT br FROM BenchmarkRun br WHERE br.benchmark.environment.id = :environmentId")
    Page<BenchmarkRun> findByEnvironmentId(@Param("environmentId") UUID environmentId, Pageable pageable);
}
