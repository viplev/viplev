package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.BenchmarkRunMapper;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.inbound.BenchmarkRunService;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BenchmarkRunServiceImpl implements BenchmarkRunService {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final EnvironmentRepository environmentRepository;
    private final AuthService authService;
    private final BenchmarkRunMapper benchmarkRunMapper;

    @Override
    public List<BenchmarkRunDTO> listBenchmarkRuns(UUID environmentId, UUID benchmarkId) {
        findEnvironmentByOwner(environmentId);
        findBenchmarkByEnvironment(benchmarkId, environmentId);
        return benchmarkRunRepository.findByBenchmarkId(benchmarkId).stream()
                .map(benchmarkRunMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void deleteBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId) {
        findEnvironmentByOwner(environmentId);
        findBenchmarkByEnvironment(benchmarkId, environmentId);
        BenchmarkRun run = benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark run not found"));
        benchmarkRunRepository.delete(run);
    }

    private Environment findEnvironmentByOwner(UUID environmentId) {
        UUID ownerId = authService.getAuthenticatedUserId();
        return environmentRepository.findByIdAndOwnerId(environmentId, ownerId)
                .orElseThrow(() -> new NotFoundException("Environment not found"));
    }

    private Benchmark findBenchmarkByEnvironment(UUID benchmarkId, UUID environmentId) {
        return benchmarkRepository.findByIdAndEnvironmentId(benchmarkId, environmentId)
                .orElseThrow(() -> new NotFoundException("Benchmark not found"));
    }
}
