package dk.viplev.api.port.inbound;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;

import java.util.List;
import java.util.UUID;

public interface BenchmarkRunService {

    List<BenchmarkRunDTO> listBenchmarkRuns(UUID environmentId, UUID benchmarkId);

    void deleteBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId);
}
