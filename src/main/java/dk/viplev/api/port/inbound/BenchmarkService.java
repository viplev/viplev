package dk.viplev.api.port.inbound;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkDTO;

import java.util.List;
import java.util.UUID;

public interface BenchmarkService {

    List<BenchmarkDTO> listBenchmarks(UUID environmentId);

    BenchmarkDTO createBenchmark(UUID environmentId, BenchmarkDTO request);

    BenchmarkDTO getBenchmark(UUID environmentId, UUID benchmarkId);

    BenchmarkDTO updateBenchmark(UUID environmentId, UUID benchmarkId, BenchmarkDTO request);

    void deleteBenchmark(UUID environmentId, UUID benchmarkId);
}
