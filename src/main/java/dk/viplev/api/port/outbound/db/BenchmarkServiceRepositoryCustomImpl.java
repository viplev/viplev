package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkService;
import dk.viplev.api.domain.model.Service;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BenchmarkServiceRepositoryCustomImpl implements BenchmarkServiceRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public int softDeleteBenchmarkService(UUID benchmarkId, UUID serviceId) {
        return entityManager.createNativeQuery("""
                        UPDATE benchmark_services
                        SET deleted_at = CURRENT_TIMESTAMP
                        WHERE benchmark_id = :benchmarkId
                          AND service_id = :serviceId
                          AND deleted_at IS NULL
                        """)
                .setParameter("benchmarkId", benchmarkId)
                .setParameter("serviceId", serviceId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void insertBenchmarkService(UUID benchmarkId, UUID serviceId) {
        BenchmarkService benchmarkService = new BenchmarkService();
        benchmarkService.setBenchmark(entityManager.getReference(Benchmark.class, benchmarkId));
        benchmarkService.setService(entityManager.getReference(Service.class, serviceId));
        benchmarkService.setCreatedAt(LocalDateTime.now());
        benchmarkService.setDeletedAt(null);
        entityManager.persist(benchmarkService);
    }
}
