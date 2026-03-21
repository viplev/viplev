package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunStatusUpdateDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceHostDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceServiceDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.BenchmarkRunMapper;
import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.Host;
import dk.viplev.api.domain.model.MetricResourceHost;
import dk.viplev.api.domain.model.MetricResourceService;
import dk.viplev.api.port.inbound.AgentService;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.HostRepository;
import dk.viplev.api.port.outbound.db.MetricResourceHostRepository;
import dk.viplev.api.port.outbound.db.MetricResourceServiceRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private final HostRepository hostRepository;
    private final ServiceRepository serviceRepository;
    private final MetricResourceHostRepository metricResourceHostRepository;
    private final MetricResourceServiceRepository metricResourceServiceRepository;
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

    @Override
    @Transactional
    public void storeResourceMetrics(UUID environmentId, UUID benchmarkId, UUID runId, MetricResourceDTO dto) {
        validateEnvironmentAccess(environmentId);

        benchmarkRepository.findByIdAndEnvironmentId(benchmarkId, environmentId)
                .orElseThrow(() -> new NotFoundException("Benchmark not found"));

        BenchmarkRun run = benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark run not found"));

        if (run.getStatus() != BenchmarkRunStatus.STARTED) {
            throw new BadRequestException("Run is not active",
                    "Cannot store metrics for a run with status " + run.getStatus());
        }

        if (dto.getHosts() != null && !dto.getHosts().isEmpty()) {
            List<MetricResourceHost> hostMetrics = new ArrayList<>();
            for (MetricResourceHostDTO hostMetricDto : dto.getHosts()) {
                Host host = hostRepository.findByIdAndEnvironmentId(hostMetricDto.getHostId(), environmentId)
                        .orElseThrow(() -> new NotFoundException("Host not found",
                                "Host with id " + hostMetricDto.getHostId() + " not found in environment"));
                hostMetrics.add(new MetricResourceHost(
                        run, host, hostMetricDto.getCollectedAt(),
                        hostMetricDto.getCpuPercentage(), hostMetricDto.getMemoryUsageBytes(),
                        hostMetricDto.getMemoryLimitBytes(), hostMetricDto.getNetworkInBytes(),
                        hostMetricDto.getNetworkOutBytes(), hostMetricDto.getBlockInBytes(),
                        hostMetricDto.getBlockOutBytes()));
            }
            metricResourceHostRepository.saveAll(hostMetrics);
        }

        if (dto.getServices() != null && !dto.getServices().isEmpty()) {
            List<MetricResourceService> serviceMetrics = new ArrayList<>();
            for (MetricResourceServiceDTO serviceMetricDto : dto.getServices()) {
                dk.viplev.api.domain.model.Service service = serviceRepository.findByIdAndHostEnvironmentId(
                                serviceMetricDto.getServiceId(), environmentId)
                        .orElseThrow(() -> new NotFoundException("Service not found",
                                "Service with id " + serviceMetricDto.getServiceId() + " not found in environment"));
                serviceMetrics.add(new MetricResourceService(
                        run, service, serviceMetricDto.getCollectedAt(),
                        serviceMetricDto.getCpuPercentage(), serviceMetricDto.getMemoryUsageBytes(),
                        serviceMetricDto.getMemoryLimitBytes(), serviceMetricDto.getNetworkInBytes(),
                        serviceMetricDto.getNetworkOutBytes(), serviceMetricDto.getBlockInBytes(),
                        serviceMetricDto.getBlockOutBytes()));
            }
            metricResourceServiceRepository.saveAll(serviceMetrics);
        }
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
