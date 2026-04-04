package dk.viplev.api.adapter.inbound.rest;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDerivedDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunRawDTO;
import dk.viplev.api.adapter.inbound.rest.dto.EnvironmentRunsDTO;
import dk.viplev.api.port.inbound.BenchmarkRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BenchmarkRunsApiDelegateImpl implements BenchmarkRunsApiDelegate {

    private final BenchmarkRunService benchmarkRunService;

    @Override
    public ResponseEntity<EnvironmentRunsDTO> listEnvironmentRuns(UUID environmentId, Integer page, Integer size, String sort) {
        return ResponseEntity.ok(benchmarkRunService.listEnvironmentRuns(environmentId, page, size, sort));
    }

    @Override
    public ResponseEntity<List<BenchmarkRunDTO>> listBenchmarkRuns(UUID environmentId, UUID benchmarkId) {
        return ResponseEntity.ok(benchmarkRunService.listBenchmarkRuns(environmentId, benchmarkId));
    }

    @Override
    public ResponseEntity<BenchmarkRunDerivedDTO> getBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId, String percentiles) {
        return ResponseEntity.ok(benchmarkRunService.getBenchmarkRunDerived(environmentId, benchmarkId, runId, percentiles));
    }

    @Override
    public ResponseEntity<BenchmarkRunRawDTO> getBenchmarkRunData(UUID environmentId, UUID benchmarkId, UUID runId) {
        return ResponseEntity.ok(benchmarkRunService.getBenchmarkRunData(environmentId, benchmarkId, runId));
    }

    @Override
    public ResponseEntity<Void> deleteBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId) {
        benchmarkRunService.deleteBenchmarkRun(environmentId, benchmarkId, runId);
        return ResponseEntity.noContent().build();
    }
}
