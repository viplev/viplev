package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunStatusUpdateDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricK6HttpDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricK6VusDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricPerformanceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricDataPointDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceNodeDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceServiceDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.BenchmarkRunMapper;
import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.Host;
import dk.viplev.api.domain.model.MetricK6Http;
import dk.viplev.api.domain.model.MetricK6Vus;
import dk.viplev.api.domain.model.MetricResourceHost;
import dk.viplev.api.domain.model.MetricResourceService;
import dk.viplev.api.domain.model.Service;
import dk.viplev.api.port.inbound.AgentService;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.HostRepository;
import dk.viplev.api.port.outbound.db.MetricK6HttpRepository;
import dk.viplev.api.port.outbound.db.MetricK6VusRepository;
import dk.viplev.api.port.outbound.db.MetricResourceHostRepository;
import dk.viplev.api.port.outbound.db.MetricResourceServiceRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
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
    private final MetricK6HttpRepository metricK6HttpRepository;
    private final MetricK6VusRepository metricK6VusRepository;
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

        if (dto.getHosts() == null) {
            throw new BadRequestException("Invalid resource metrics", "hosts must not be null");
        }

        // Process metrics for each node in the payload
        for (MetricResourceNodeDTO node : dto.getHosts()) {
            if (node == null) {
                throw new BadRequestException("Invalid resource metrics", "hosts must not contain null entries");
            }
            if (node.getMachineId() == null || node.getMachineId().isBlank()) {
                throw new BadRequestException("Invalid resource metrics", "machineId must not be null or blank");
            }
            if (node.getMetrics() == null) {
                throw new BadRequestException("Invalid resource metrics", "metrics must not be null for host " + node.getMachineId());
            }
            String machineId = node.getMachineId();
            Host host = hostRepository.findByEnvironmentIdAndMachineId(environmentId, machineId)
                    .orElseThrow(() -> new NotFoundException("Host not found",
                            "Host with machineId " + machineId + " not found in environment"));

            List<MetricResourceHost> hostMetrics = new ArrayList<>();
            for (MetricDataPointDTO dp : node.getMetrics()) {
                hostMetrics.add(new MetricResourceHost(
                        run, host, dp.getCollectedAt(),
                        dp.getCpuPercentage(), dp.getMemoryUsageBytes(),
                        dp.getMemoryLimitBytes(), dp.getNetworkInBytes(),
                        dp.getNetworkOutBytes(), dp.getBlockInBytes(),
                        dp.getBlockOutBytes()));
            }
            metricResourceHostRepository.saveAll(hostMetrics);

            // Service metrics — batch-fetch all services for the host in one query
            if (node.getServices() != null && !node.getServices().isEmpty()) {
                for (MetricResourceServiceDTO serviceDto : node.getServices()) {
                    if (serviceDto == null) {
                        throw new BadRequestException("Invalid resource metrics", "service entry must not be null");
                    }
                    if (serviceDto.getServiceName() == null || serviceDto.getServiceName().isBlank()) {
                        throw new BadRequestException("Invalid resource metrics", "serviceName must not be null or blank");
                    }
                    if (serviceDto.getMetrics() == null) {
                        throw new BadRequestException("Invalid resource metrics", "metrics must not be null for service " + serviceDto.getServiceName());
                    }
                }

                Set<String> serviceNames = node.getServices().stream()
                        .map(MetricResourceServiceDTO::getServiceName)
                        .collect(Collectors.toSet());

                Map<String, Service> servicesByName = serviceRepository
                        .findByHostIdAndServiceNameIn(host.getId(), serviceNames).stream()
                        .collect(Collectors.toMap(Service::getServiceName, Function.identity()));

                for (String name : serviceNames) {
                    if (!servicesByName.containsKey(name)) {
                        throw new NotFoundException("Service not found",
                                "Service with name " + name + " not found on host");
                    }
                }

                List<MetricResourceService> serviceMetrics = new ArrayList<>();
                for (MetricResourceServiceDTO serviceDto : node.getServices()) {
                    Service service = servicesByName.get(serviceDto.getServiceName());

                    for (MetricDataPointDTO dp : serviceDto.getMetrics()) {
                        serviceMetrics.add(new MetricResourceService(
                                run, service, dp.getCollectedAt(),
                                dp.getCpuPercentage(), dp.getMemoryUsageBytes(),
                                dp.getMemoryLimitBytes(), dp.getNetworkInBytes(),
                                dp.getNetworkOutBytes(), dp.getBlockInBytes(),
                                dp.getBlockOutBytes()));
                    }
                }
                metricResourceServiceRepository.saveAll(serviceMetrics);
            }
        }
    }

    @Override
    @Transactional
    public void storePerformanceMetrics(UUID environmentId, UUID benchmarkId, UUID runId, MetricPerformanceDTO dto) {
        validateEnvironmentAccess(environmentId);

        benchmarkRepository.findByIdAndEnvironmentId(benchmarkId, environmentId)
                .orElseThrow(() -> new NotFoundException("Benchmark not found"));

        BenchmarkRun run = benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark run not found"));

        if (run.getStatus() != BenchmarkRunStatus.STARTED) {
            throw new BadRequestException("Run is not active",
                    "Cannot store metrics for a run with status " + run.getStatus());
        }

        if (dto.getHttpMetrics() != null && !dto.getHttpMetrics().isEmpty()) {
            List<MetricK6Http> httpMetrics = new ArrayList<>();
            for (MetricK6HttpDTO httpDto : dto.getHttpMetrics()) {
                httpMetrics.add(new MetricK6Http(
                        run, httpDto.getCollectedAt(), httpDto.getUrl(),
                        httpDto.getHttpMethod().getValue(),
                        httpDto.getRequestGroup(), httpDto.getHttpStatus(),
                        httpDto.getExpectedStatus(), httpDto.getDataReceivedByte(),
                        httpDto.getDataSentByte(), httpDto.getHttpReqDurationMs(),
                        httpDto.getHttpReqWaitingMs()));
            }
            metricK6HttpRepository.saveAll(httpMetrics);
        }

        if (dto.getVusMetrics() != null && !dto.getVusMetrics().isEmpty()) {
            List<MetricK6Vus> vusMetrics = new ArrayList<>();
            for (MetricK6VusDTO vusDto : dto.getVusMetrics()) {
                vusMetrics.add(new MetricK6Vus(run, vusDto.getCollectedAt(), vusDto.getVus()));
            }
            metricK6VusRepository.saveAll(vusMetrics);
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
