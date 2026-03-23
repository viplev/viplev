package dk.viplev.api.port.inbound;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDerivedDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunRawDTO;

import java.util.List;
import java.util.UUID;

public interface BenchmarkRunService {

    List<BenchmarkRunDTO> listBenchmarkRuns(UUID environmentId, UUID benchmarkId);

    BenchmarkRunDerivedDTO getBenchmarkRunDerived(UUID environmentId, UUID benchmarkId, UUID runId, String percentiles);

    BenchmarkRunRawDTO getBenchmarkRunRaw(UUID environmentId, UUID benchmarkId, UUID runId);

    void deleteBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId);
}
