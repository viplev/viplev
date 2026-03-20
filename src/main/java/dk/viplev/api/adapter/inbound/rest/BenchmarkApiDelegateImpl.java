package dk.viplev.api.adapter.inbound.rest;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkDTO;
import dk.viplev.api.port.inbound.BenchmarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BenchmarkApiDelegateImpl implements BenchmarkApiDelegate {

    private final BenchmarkService benchmarkService;

    @Override
    public ResponseEntity<List<BenchmarkDTO>> listBenchmarks(UUID environmentId) {
        return ResponseEntity.ok(benchmarkService.listBenchmarks(environmentId));
    }

    @Override
    public ResponseEntity<BenchmarkDTO> createBenchmark(UUID environmentId, BenchmarkDTO benchmarkDTO) {
        BenchmarkDTO created = benchmarkService.createBenchmark(environmentId, benchmarkDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<BenchmarkDTO> getBenchmark(UUID benchmarkId, UUID environmentId) {
        return ResponseEntity.ok(benchmarkService.getBenchmark(environmentId, benchmarkId));
    }

    @Override
    public ResponseEntity<BenchmarkDTO> updateBenchmark(UUID benchmarkId, UUID environmentId, BenchmarkDTO benchmarkDTO) {
        return ResponseEntity.ok(benchmarkService.updateBenchmark(environmentId, benchmarkId, benchmarkDTO));
    }

    @Override
    public ResponseEntity<Void> deleteBenchmark(UUID benchmarkId, UUID environmentId) {
        benchmarkService.deleteBenchmark(environmentId, benchmarkId);
        return ResponseEntity.noContent().build();
    }
}
