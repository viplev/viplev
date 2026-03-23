package dk.viplev.api.port.inbound;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDerivedDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunRawDTO;
import dk.viplev.api.adapter.inbound.rest.dto.EnvironmentRunsDTO;

import java.util.List;
import java.util.UUID;

public interface BenchmarkRunService {

    List<BenchmarkRunDTO> listBenchmarkRuns(UUID environmentId, UUID benchmarkId);

    EnvironmentRunsDTO listEnvironmentRuns(UUID environmentId, Integer page, Integer size, String sort);

    BenchmarkRunDerivedDTO getBenchmarkRunDerived(UUID environmentId, UUID benchmarkId, UUID runId, String percentiles);

    BenchmarkRunRawDTO getBenchmarkRunRaw(UUID environmentId, UUID benchmarkId, UUID runId);

    void deleteBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId);
}
