package dk.viplev.api.port.outbound.db;

import java.util.UUID;

public interface BenchmarkServiceRepositoryCustom {

    int softDeleteBenchmarkService(UUID benchmarkId, UUID serviceId);

    void insertBenchmarkService(UUID benchmarkId, UUID serviceId);
}
