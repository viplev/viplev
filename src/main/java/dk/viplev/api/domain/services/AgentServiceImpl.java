package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunStatusUpdateDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.BenchmarkRunMapper;
import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.port.inbound.AgentService;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private static final Map<BenchmarkRunStatus, Set<BenchmarkRunStatus>> VALID_TRANSITIONS = Map.of(
            BenchmarkRunStatus.PENDING_START, Set.of(BenchmarkRunStatus.STARTED, BenchmarkRunStatus.FAILED),
            BenchmarkRunStatus.STARTED, Set.of(BenchmarkRunStatus.FINISHED, BenchmarkRunStatus.FAILED),
            BenchmarkRunStatus.PENDING_STOP, Set.of(BenchmarkRunStatus.STOPPED, BenchmarkRunStatus.FAILED)
    );

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final AuthService authService;
    private final BenchmarkRunMapper benchmarkRunMapper;

    @Override
    @Transactional
    public BenchmarkRunDTO updateBenchmarkRunStatus(UUID environmentId, UUID benchmarkId, UUID runId, BenchmarkRunStatusUpdateDTO dto) {
        validateEnvironmentAccess(environmentId);

        benchmarkRepository.findByIdAndEnvironmentId(benchmarkId, environmentId)
                .orElseThrow(() -> new NotFoundException("Benchmark not found"));

        BenchmarkRun run = benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark run not found"));

        BenchmarkRunStatus newStatus = BenchmarkRunStatus.valueOf(dto.getStatus().name());

        validateTransition(run.getStatus(), newStatus);
        validateStatusReason(newStatus, dto.getStatusReason());

        run.setStatus(newStatus);
        run.setStatusReason(dto.getStatusReason());

        if (newStatus == BenchmarkRunStatus.STARTED) {
            run.setStartedAt(LocalDateTime.now());
        }
        if (newStatus == BenchmarkRunStatus.FINISHED || newStatus == BenchmarkRunStatus.STOPPED || newStatus == BenchmarkRunStatus.FAILED) {
            run.setFinishedAt(LocalDateTime.now());
        }

        benchmarkRunRepository.save(run);

        return benchmarkRunMapper.toDto(run);
    }

    private void validateEnvironmentAccess(UUID environmentId) {
        UUID tokenEnvironmentId = authService.getAuthenticatedEnvironmentId();
        if (!environmentId.equals(tokenEnvironmentId)) {
            throw new NotFoundException("Environment not found");
        }
    }

    private void validateTransition(BenchmarkRunStatus currentStatus, BenchmarkRunStatus newStatus) {
        Set<BenchmarkRunStatus> allowedTransitions = VALID_TRANSITIONS.get(currentStatus);
        if (allowedTransitions == null || !allowedTransitions.contains(newStatus)) {
            throw new BadRequestException("Invalid status transition",
                    "Cannot transition from " + currentStatus + " to " + newStatus);
        }
    }

    private void validateStatusReason(BenchmarkRunStatus newStatus, String statusReason) {
        if (newStatus == BenchmarkRunStatus.FAILED && (statusReason == null || statusReason.isBlank())) {
            throw new BadRequestException("Status reason required",
                    "statusReason is required when status is FAILED");
        }
    }
}
