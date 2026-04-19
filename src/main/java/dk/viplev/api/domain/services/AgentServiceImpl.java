package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunStatusUpdateDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MessageDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricK6HttpDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricK6VusDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricPerformanceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricDataPointDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceNodeDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceServiceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceServiceReplicaDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.BenchmarkMapper;
import dk.viplev.api.adapter.inbound.rest.mapper.BenchmarkRunMapper;
import dk.viplev.api.adapter.inbound.rest.mapper.MessageMapper;
import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.domain.model.Host;
import dk.viplev.api.domain.model.MetricK6Http;
import dk.viplev.api.domain.model.MetricK6Vus;
import dk.viplev.api.domain.model.MetricResourceHost;
import dk.viplev.api.domain.model.MetricResourceReplica;
import dk.viplev.api.domain.model.Service;
import dk.viplev.api.domain.model.ServiceReplica;
import dk.viplev.api.port.inbound.AgentService;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;
import dk.viplev.api.port.outbound.db.HostRepository;
import dk.viplev.api.port.outbound.db.MetricK6HttpRepository;
import dk.viplev.api.port.outbound.db.MetricK6VusRepository;
import dk.viplev.api.port.outbound.db.MetricResourceHostRepository;
import dk.viplev.api.port.outbound.db.MetricResourceReplicaRepository;
import dk.viplev.api.port.outbound.db.ServiceReplicaRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
@Slf4j
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
    private final ServiceReplicaRepository serviceReplicaRepository;
    private final MetricResourceHostRepository metricResourceHostRepository;
    private final MetricResourceReplicaRepository metricResourceReplicaRepository;
    private final MetricK6HttpRepository metricK6HttpRepository;
    private final MetricK6VusRepository metricK6VusRepository;
    private final EnvironmentRepository environmentRepository;
    private final AuthService authService;
    private final BenchmarkMapper benchmarkMapper;
    private final BenchmarkRunMapper benchmarkRunMapper;
    private final MessageMapper messageMapper;

    @Override
    @Transactional
    public List<MessageDTO> listMessages(UUID environmentId) {
        log.info("Listing agent messages: environmentId={}", environmentId);
        validateEnvironmentAccess(environmentId);

        Environment environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new NotFoundException("Environment not found"));
        environment.setAgentLastSeenAt(LocalDateTime.now());
        environmentRepository.save(environment);
        log.info("Agent heartbeat updated: environmentId={}, agentLastSeenAt={}",
                environmentId, environment.getAgentLastSeenAt());

        BenchmarkRun pendingStopRun = benchmarkRunRepository
                .findFirstByBenchmarkEnvironmentIdAndStatusOrderByCreatedAtAsc(environmentId, BenchmarkRunStatus.PENDING_STOP)
                .orElse(null);

        if (pendingStopRun != null) {
            MessageDTO message = toMessageDto(pendingStopRun);
            log.info("Returning agent message: environmentId={}, messageType={}, benchmarkId={}, runId={}",
                    environmentId, message.getMessageType(), message.getBenchmarkId(), message.getRunId());
            return List.of(message);
        }

        BenchmarkRun pendingStartRun = benchmarkRunRepository
                .findFirstByBenchmarkEnvironmentIdAndStatusOrderByCreatedAtAsc(environmentId, BenchmarkRunStatus.PENDING_START)
                .orElse(null);

        if (pendingStartRun != null) {
            MessageDTO message = toMessageDto(pendingStartRun);
            log.info("Returning agent message: environmentId={}, messageType={}, benchmarkId={}, runId={}",
                    environmentId, message.getMessageType(), message.getBenchmarkId(), message.getRunId());
            return List.of(message);
        }

        log.info("No pending agent message found: environmentId={}", environmentId);
        return List.of();
    }

    private MessageDTO toMessageDto(BenchmarkRun run) {
        MessageDTO dto = messageMapper.toDto(run);
        if (run.getStatus() == BenchmarkRunStatus.PENDING_START && run.getBenchmark() != null) {
            dto.setBenchmarkData(benchmarkMapper.toDto(run.getBenchmark()));
        }
        return dto;
    }

    @Override
    @Transactional
    public BenchmarkRunDTO updateBenchmarkRunStatus(UUID environmentId, UUID benchmarkId, UUID runId, BenchmarkRunStatusUpdateDTO dto) {
        validateEnvironmentAccess(environmentId);

        benchmarkRepository.findByIdAndEnvironmentId(benchmarkId, environmentId)
                .orElseThrow(() -> new NotFoundException("Benchmark not found"));

        BenchmarkRun run = benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark run not found"));

        BenchmarkRunStatus currentStatus = run.getStatus();
        BenchmarkRunStatus newStatus = BenchmarkRunStatus.valueOf(dto.getStatus().name());

        log.info("Run status update requested: environmentId={}, benchmarkId={}, runId={}, currentStatus={}, requestedStatus={}, statusReason={}",
                environmentId, benchmarkId, runId, currentStatus, newStatus, dto.getStatusReason());

        validateTransition(currentStatus, newStatus);
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
        log.info("Run status update persisted: environmentId={}, benchmarkId={}, runId={}, previousStatus={}, persistedStatus={}, startedAt={}, finishedAt={}, statusReason={}",
                environmentId, benchmarkId, runId, currentStatus, run.getStatus(), run.getStartedAt(), run.getFinishedAt(), run.getStatusReason());

        return benchmarkRunMapper.toDto(run);
    }

    @Override
    @Transactional
    public void storeResourceMetrics(UUID environmentId, UUID benchmarkId, UUID runId, MetricResourceDTO dto) {
        if (dto == null) {
            throw new BadRequestException("Invalid resource metrics", "request body must not be null");
        }

        int hostCount = dto != null && dto.getHosts() != null ? dto.getHosts().size() : 0;
        log.info("Storing resource metrics: environmentId={}, benchmarkId={}, runId={}, hostCount={}",
                environmentId, benchmarkId, runId, hostCount);

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

        int totalHostMetricDataPoints = 0;
        int totalReplicaMetricDataPoints = 0;

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
                if (dp == null) {
                    throw new BadRequestException("Invalid resource metrics",
                            "metrics must not contain null entries for host " + node.getMachineId());
                }
                hostMetrics.add(new MetricResourceHost(
                        run, host, dp.getCollectedAt(),
                        dp.getCpuPercentage(), dp.getMemoryUsageBytes(),
                        dp.getMemoryLimitBytes(), dp.getNetworkInBytes(),
                        dp.getNetworkOutBytes(), dp.getBlockInBytes(),
                        dp.getBlockOutBytes()));
            }
            metricResourceHostRepository.saveAll(hostMetrics);
            totalHostMetricDataPoints += hostMetrics.size();

            // Replica metrics — batch-fetch all services for the host in one query
            int currentHostReplicaMetricDataPoints = 0;
            if (node.getServices() != null && !node.getServices().isEmpty()) {
                for (MetricResourceServiceDTO serviceDto : node.getServices()) {
                    if (serviceDto == null) {
                        throw new BadRequestException("Invalid resource metrics", "service entry must not be null");
                    }
                    if (serviceDto.getServiceName() == null || serviceDto.getServiceName().isBlank()) {
                        throw new BadRequestException("Invalid resource metrics", "serviceName must not be null or blank");
                    }
                    if (serviceDto.getReplicas() == null) {
                        throw new BadRequestException("Invalid resource metrics", "replicas must not be null for service " + serviceDto.getServiceName());
                    }
                }

                Set<String> serviceNames = node.getServices().stream()
                        .map(MetricResourceServiceDTO::getServiceName)
                        .collect(Collectors.toSet());

                // Only fetch active services (not soft-deleted)
                Map<String, Service> servicesByName = serviceRepository
                        .findByHostIdAndServiceNameInAndDeletedAtIsNull(host.getId(), serviceNames).stream()
                        .collect(Collectors.toMap(Service::getServiceName, Function.identity()));

                for (String name : serviceNames) {
                    if (!servicesByName.containsKey(name)) {
                        throw new NotFoundException("Service not found",
                                "Service with name " + name + " not found on host or is soft-deleted");
                    }
                }

                // Accumulate all replicas and metrics for this host
                List<MetricResourceReplica> allReplicaMetrics = new ArrayList<>();
                List<ServiceReplica> allReplicasToUpdate = new ArrayList<>();
                
                for (MetricResourceServiceDTO serviceDto : node.getServices()) {
                    Service service = servicesByName.get(serviceDto.getServiceName());

                    // Validate replicas array is not empty
                    if (serviceDto.getReplicas() == null || serviceDto.getReplicas().isEmpty()) {
                        throw new BadRequestException("Invalid resource metrics", 
                                "replicas must not be null or empty for service " + serviceDto.getServiceName());
                    }

                    // Store replica-level metrics
                    for (MetricResourceServiceReplicaDTO replicaDto : serviceDto.getReplicas()) {
                        if (replicaDto == null) {
                            throw new BadRequestException("Invalid resource metrics", "replica entry must not be null");
                        }
                        if (replicaDto.getContainerId() == null || replicaDto.getContainerId().isBlank()) {
                            throw new BadRequestException("Invalid resource metrics", "containerId must not be null or blank");
                        }
                        if (replicaDto.getMetrics() == null) {
                            throw new BadRequestException("Invalid resource metrics", "metrics must not be null for replica " + replicaDto.getContainerId());
                        }

                        // Find or create replica (with race condition handling)
                        ServiceReplica replica = findOrCreateReplica(service, replicaDto);

                        // Reactivate if soft-deleted
                        if (replica.getDeletedAt() != null) {
                            replica.setDeletedAt(null);
                            log.debug("Reactivating replica: containerId={}, replicaId={}", replicaDto.getContainerId(), replica.getId());
                        }

                        // Update last seen
                        replica.setLastSeenAt(LocalDateTime.now());
                        if (replicaDto.getStartedAt() != null) {
                            replica.setStartedAt(replicaDto.getStartedAt());
                        }
                        allReplicasToUpdate.add(replica);

                        // Store metrics for this replica
                        for (MetricDataPointDTO dp : replicaDto.getMetrics()) {
                            if (dp == null) {
                                throw new BadRequestException("Invalid resource metrics",
                                        "metrics must not contain null entries for replica " + replicaDto.getContainerId());
                            }
                            allReplicaMetrics.add(new MetricResourceReplica(
                                    run, replica, dp.getCollectedAt(),
                                    dp.getCpuPercentage(), dp.getMemoryUsageBytes(),
                                    dp.getMemoryLimitBytes(), dp.getNetworkInBytes(),
                                    dp.getNetworkOutBytes(), dp.getBlockInBytes(),
                                    dp.getBlockOutBytes()));
                        }
                    }
                }
                
                // Batch save all replicas and metrics for this host
                serviceReplicaRepository.saveAll(allReplicasToUpdate);
                metricResourceReplicaRepository.saveAll(allReplicaMetrics);
                currentHostReplicaMetricDataPoints = allReplicaMetrics.size();
                totalReplicaMetricDataPoints += currentHostReplicaMetricDataPoints;
            }

            int serviceCount = node.getServices() != null ? node.getServices().size() : 0;
            log.debug("Stored resource metrics for host: environmentId={}, benchmarkId={}, runId={}, machineId={}, hostDataPoints={}, serviceCount={}, replicaDataPoints={}",
                    environmentId, benchmarkId, runId, machineId, hostMetrics.size(), serviceCount, currentHostReplicaMetricDataPoints);
        }

        log.info("Resource metrics storage completed: environmentId={}, benchmarkId={}, runId={}, hostCount={}, totalHostDataPoints={}, totalReplicaDataPoints={}",
                environmentId, benchmarkId, runId, dto.getHosts().size(), totalHostMetricDataPoints, totalReplicaMetricDataPoints);
    }

    @Override
    @Transactional
    public void storePerformanceMetrics(UUID environmentId, UUID benchmarkId, UUID runId, MetricPerformanceDTO dto) {
        if (dto == null) {
            throw new BadRequestException("Invalid performance metrics", "request body must not be null");
        }

        int httpMetricCount = dto != null && dto.getHttpMetrics() != null ? dto.getHttpMetrics().size() : 0;
        int vusMetricCount = dto != null && dto.getVusMetrics() != null ? dto.getVusMetrics().size() : 0;
        log.info("Storing performance metrics: environmentId={}, benchmarkId={}, runId={}, httpMetricCount={}, vusMetricCount={}",
                environmentId, benchmarkId, runId, httpMetricCount, vusMetricCount);

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

        log.info("Performance metrics storage completed: environmentId={}, benchmarkId={}, runId={}, persistedHttpMetrics={}, persistedVusMetrics={}",
                environmentId, benchmarkId, runId, httpMetricCount, vusMetricCount);
    }

    private ServiceReplica findOrCreateReplica(Service service, MetricResourceServiceReplicaDTO replicaDto) {
        // Try to find existing replica first
        var existing = serviceReplicaRepository.findByServiceIdAndContainerId(service.getId(), replicaDto.getContainerId());
        if (existing.isPresent()) {
            return existing.get();
        }

        // Replica doesn't exist - try to create it
        try {
            ServiceReplica newReplica = new ServiceReplica();
            newReplica.setService(service);
            newReplica.setContainerId(replicaDto.getContainerId());
            newReplica.setStartedAt(replicaDto.getStartedAt());
            newReplica.setLastSeenAt(LocalDateTime.now());
            return serviceReplicaRepository.save(newReplica);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread created it between our check and save
            // Retry the lookup - it should exist now
            log.debug("Race condition detected creating replica, retrying lookup: containerId={}", replicaDto.getContainerId());
            return serviceReplicaRepository.findByServiceIdAndContainerId(service.getId(), replicaDto.getContainerId())
                    .orElseThrow(() -> new BadRequestException("Failed to create or find replica after race condition",
                            "containerId: " + replicaDto.getContainerId()));
        }
    }

    private void validateEnvironmentAccess(UUID environmentId) {
        UUID tokenEnvironmentId = authService.getAuthenticatedEnvironmentId();
        if (!environmentId.equals(tokenEnvironmentId)) {
            log.info("Environment access denied for agent request: environmentId={}", environmentId);
            log.debug("Environment access denied details: environmentId={}, tokenEnvironmentId={}",
                    environmentId, tokenEnvironmentId);
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
