package dk.viplev.api.port.inbound;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkStatusDTO;

import java.util.UUID;

public interface BenchmarkActionService {

    BenchmarkStatusDTO startBenchmark(UUID environmentId, UUID benchmarkId);

    BenchmarkStatusDTO stopBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId);
}
