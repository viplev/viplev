package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkStatusDTO;
import dk.viplev.api.domain.exception.ConflictException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.inbound.BenchmarkActionService;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;
import dk.viplev.api.port.outbound.db.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BenchmarkActionServiceImpl implements BenchmarkActionService {

    private static final Set<BenchmarkRunStatus> ACTIVE_STATUSES = Set.of(
            BenchmarkRunStatus.PENDING_START,
            BenchmarkRunStatus.STARTED,
            BenchmarkRunStatus.PENDING_STOP
    );

    private static final Set<BenchmarkRunStatus> STOPPABLE_STATUSES = Set.of(
            BenchmarkRunStatus.PENDING_START,
            BenchmarkRunStatus.STARTED
    );

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final EnvironmentRepository environmentRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Override
    @Transactional
    public BenchmarkStatusDTO startBenchmark(UUID environmentId, UUID benchmarkId) {
        findEnvironmentByOwner(environmentId);
        Benchmark benchmark = findBenchmarkByEnvironment(benchmarkId, environmentId);

        boolean hasActiveRun = benchmarkRunRepository.existsByBenchmarkIdAndStatusIn(benchmarkId, ACTIVE_STATUSES);
        if (hasActiveRun) {
            throw new ConflictException("Benchmark already has an active run",
                    "Benchmark " + benchmarkId + " already has an active run");
        }

        UUID userId = authService.getAuthenticatedUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        BenchmarkRun run = new BenchmarkRun();
        run.setBenchmark(benchmark);
        run.setStartedByUser(user);
        run.setStatus(BenchmarkRunStatus.PENDING_START);

        benchmarkRunRepository.save(run);

        return toBenchmarkStatusDTO(benchmarkId, run);
    }

    @Override
    @Transactional
    public BenchmarkStatusDTO stopBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId) {
        findEnvironmentByOwner(environmentId);
        findBenchmarkByEnvironment(benchmarkId, environmentId);

        BenchmarkRun run = benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark run not found"));

        if (!STOPPABLE_STATUSES.contains(run.getStatus())) {
            throw new ConflictException("Benchmark run is not in a stoppable state",
                    "Run status is " + run.getStatus() + ", expected PENDING_START or STARTED");
        }

        if (run.getStatus() == BenchmarkRunStatus.PENDING_START) {
            run.setStatus(BenchmarkRunStatus.STOPPED);
            run.setFinishedAt(LocalDateTime.now());
        } else {
            run.setStatus(BenchmarkRunStatus.PENDING_STOP);
        }
        benchmarkRunRepository.save(run);

        return toBenchmarkStatusDTO(benchmarkId, run);
    }

    private BenchmarkStatusDTO toBenchmarkStatusDTO(UUID benchmarkId, BenchmarkRun run) {
        BenchmarkStatusDTO dto = new BenchmarkStatusDTO();
        dto.setBenchmarkId(benchmarkId);
        dto.setRunId(run.getId());
        dto.setStatus(BenchmarkStatusDTO.StatusEnum.valueOf(run.getStatus().name()));
        return dto;
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
