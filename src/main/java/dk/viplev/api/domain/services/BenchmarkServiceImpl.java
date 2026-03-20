package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.BenchmarkMapper;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.inbound.BenchmarkService;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
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
public class BenchmarkServiceImpl implements BenchmarkService {

    private final BenchmarkRepository benchmarkRepository;
    private final EnvironmentRepository environmentRepository;
    private final AuthService authService;
    private final BenchmarkMapper benchmarkMapper;

    @Override
    public List<BenchmarkDTO> listBenchmarks(UUID environmentId) {
        Environment environment = findEnvironmentByOwner(environmentId);
        return benchmarkRepository.findByEnvironmentId(environment.getId()).stream()
                .map(benchmarkMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public BenchmarkDTO createBenchmark(UUID environmentId, BenchmarkDTO request) {
        Environment environment = findEnvironmentByOwner(environmentId);

        Benchmark benchmark = new Benchmark();
        benchmark.setId(UUID.randomUUID());
        benchmark.setName(request.getName());
        benchmark.setDescription(request.getDescription());
        benchmark.setK6Instructions(request.getK6Instructions());
        benchmark.setEnvironment(environment);

        Benchmark saved = benchmarkRepository.saveAndFlush(benchmark);
        return benchmarkMapper.toDto(saved);
    }

    @Override
    public BenchmarkDTO getBenchmark(UUID environmentId, UUID benchmarkId) {
        findEnvironmentByOwner(environmentId);
        Benchmark benchmark = findBenchmarkByEnvironment(benchmarkId, environmentId);
        return benchmarkMapper.toDto(benchmark);
    }

    @Override
    @Transactional
    public BenchmarkDTO updateBenchmark(UUID environmentId, UUID benchmarkId, BenchmarkDTO request) {
        findEnvironmentByOwner(environmentId);
        Benchmark benchmark = findBenchmarkByEnvironment(benchmarkId, environmentId);

        benchmark.setName(request.getName());
        benchmark.setDescription(request.getDescription());
        benchmark.setK6Instructions(request.getK6Instructions());

        Benchmark saved = benchmarkRepository.save(benchmark);
        return benchmarkMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteBenchmark(UUID environmentId, UUID benchmarkId) {
        findEnvironmentByOwner(environmentId);
        Benchmark benchmark = findBenchmarkByEnvironment(benchmarkId, environmentId);
        benchmarkRepository.delete(benchmark);
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
