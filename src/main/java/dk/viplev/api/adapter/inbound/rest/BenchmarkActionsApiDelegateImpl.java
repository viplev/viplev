package dk.viplev.api.adapter.inbound.rest;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkStatusDTO;
import dk.viplev.api.port.inbound.BenchmarkActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BenchmarkActionsApiDelegateImpl implements BenchmarkActionsApiDelegate {

    private final BenchmarkActionService benchmarkActionService;

    @Override
    public ResponseEntity<BenchmarkStatusDTO> startBenchmark(UUID environmentId, UUID benchmarkId) {
        BenchmarkStatusDTO dto = benchmarkActionService.startBenchmark(environmentId, benchmarkId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Override
    public ResponseEntity<BenchmarkStatusDTO> stopBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId) {
        BenchmarkStatusDTO dto = benchmarkActionService.stopBenchmarkRun(environmentId, benchmarkId, runId);
        return ResponseEntity.ok(dto);
    }
}
