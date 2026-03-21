package dk.viplev.api.adapter.inbound.rest;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.port.inbound.BenchmarkRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BenchmarkRunsApiDelegateImpl implements BenchmarkRunsApiDelegate {

    private final BenchmarkRunService benchmarkRunService;

    @Override
    public ResponseEntity<List<BenchmarkRunDTO>> listBenchmarkRuns(UUID environmentId, UUID benchmarkId) {
        return ResponseEntity.ok(benchmarkRunService.listBenchmarkRuns(environmentId, benchmarkId));
    }

    @Override
    public ResponseEntity<Void> deleteBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId) {
        benchmarkRunService.deleteBenchmarkRun(environmentId, benchmarkId, runId);
        return ResponseEntity.noContent().build();
    }
}
