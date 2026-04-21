package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.HostDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceRegistrationDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceRegistrationServiceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceReplicaDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.ServiceMapper;
import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.Host;
import dk.viplev.api.domain.model.ServiceReplica;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.inbound.ServiceService;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;
import dk.viplev.api.port.outbound.db.HostRepository;
import dk.viplev.api.port.outbound.db.ServiceReplicaRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class ServiceServiceImpl implements ServiceService {

    private final ServiceRepository serviceRepository;
    private final ServiceReplicaRepository serviceReplicaRepository;
    private final HostRepository hostRepository;
    private final EnvironmentRepository environmentRepository;
    private final ServiceMapper serviceMapper;
    private final AuthService authService;

    @Override
    public List<ServiceDTO> listServices(UUID environmentId) {
        verifyEnvironmentOwnership(environmentId);
        return serviceMapper.toDtoList(serviceRepository.findByEnvironmentIdAndDeletedAtIsNull(environmentId));
    }

    @Override
    public ServiceDTO getService(UUID environmentId, UUID serviceId) {
        verifyEnvironmentOwnership(environmentId);
        var service = serviceRepository.findByIdAndEnvironmentId(serviceId, environmentId)
                .orElseThrow(() -> new NotFoundException("Service not found"));
        return serviceMapper.toDto(service);
    }

    @Override
    @Transactional
    public void registerServices(UUID environmentId, ServiceRegistrationDTO registration) {
        if (registration == null) {
            throw new BadRequestException("Invalid registration", "body must not be null");
        }

        int hostCount = registration.getHosts() != null ? registration.getHosts().size() : 0;
        int serviceCount = registration.getServices() != null ? registration.getServices().size() : 0;
        log.info("Registering services for environment: environmentId={}, hostCount={}, serviceCount={}",
                environmentId, hostCount, serviceCount);

        UUID agentEnvironmentId = authService.getAuthenticatedEnvironmentId();
        if (agentEnvironmentId == null || !agentEnvironmentId.equals(environmentId)) {
            throw new BadRequestException("Agent token does not match the environment");
        }

        if (registration.getHosts() == null) {
            throw new BadRequestException("Invalid registration", "hosts must not be null");
        }

        if (registration.getServices() == null) {
            throw new BadRequestException("Invalid registration", "services must not be null");
        }

        var environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new NotFoundException("Environment not found"));

        // Step 1: Validate incoming payload
        validateRegistration(registration);

        // Step 2: Upsert all hosts
        Map<String, Host> hostsByMachineId = upsertHosts(environment, registration.getHosts());

        // Step 3: Collect all unique service names from incoming services
        Set<String> incomingServiceNames = registration.getServices().stream()
                .map(ServiceRegistrationServiceDTO::getServiceName)
                .collect(Collectors.toSet());

        // Step 4: Get existing services by environmentId
        Map<String, dk.viplev.api.domain.model.Service> existingServicesByName =
                serviceRepository.findByEnvironmentId(environmentId).stream()
                        .collect(Collectors.toMap(dk.viplev.api.domain.model.Service::getServiceName, s -> s));

        int servicesCreated = 0;
        int servicesUpdated = 0;
        int servicesReactivated = 0;
        int replicasCreated = 0;
        int replicasUpdated = 0;
        int replicasReactivated = 0;
        int replicasSoftDeleted = 0;

        // Declare now once for the entire method
        LocalDateTime now = LocalDateTime.now();

        // Step 5: For each incoming service, upsert service and replicas
        for (ServiceRegistrationServiceDTO serviceDto : registration.getServices()) {
            var existing = existingServicesByName.get(serviceDto.getServiceName());
            dk.viplev.api.domain.model.Service service;

            if (existing != null) {
                // Service exists - check if it's soft-deleted
                if (existing.getDeletedAt() != null) {
                    // Reactivate soft-deleted service
                    existing.setDeletedAt(null);
                    servicesReactivated++;
                    log.debug("Reactivating service: serviceName={}, serviceId={}", serviceDto.getServiceName(), existing.getId());
                } else {
                    servicesUpdated++;
                }
                // Update fields
                existing.setImageSha(serviceDto.getImageSha());
                existing.setImageName(serviceDto.getImageName());
                existing.setCpuLimit(serviceDto.getCpuLimit());
                existing.setCpuReservation(serviceDto.getCpuReservation());
                existing.setMemoryLimitBytes(serviceDto.getMemoryLimitBytes());
                existing.setMemoryReservationBytes(serviceDto.getMemoryReservationBytes());
                service = serviceRepository.save(existing);
            } else {
                // Create new service
                var svc = new dk.viplev.api.domain.model.Service();
                svc.setEnvironment(environment);
                svc.setServiceName(serviceDto.getServiceName());
                svc.setImageSha(serviceDto.getImageSha());
                svc.setImageName(serviceDto.getImageName());
                svc.setCpuLimit(serviceDto.getCpuLimit());
                svc.setCpuReservation(serviceDto.getCpuReservation());
                svc.setMemoryLimitBytes(serviceDto.getMemoryLimitBytes());
                svc.setMemoryReservationBytes(serviceDto.getMemoryReservationBytes());
                service = serviceRepository.save(svc);
                servicesCreated++;
                log.debug("Created service: serviceName={}, serviceId={}", serviceDto.getServiceName(), service.getId());
            }

            // Upsert replicas for this service
            if (serviceDto.getReplicas() != null) {
                Set<String> incomingContainerIds = serviceDto.getReplicas().stream()
                        .map(ServiceReplicaDTO::getContainerId)
                        .collect(Collectors.toSet());

                Map<String, ServiceReplica> existingReplicasByContainerId =
                        serviceReplicaRepository.findByServiceIdAndContainerIdIn(service.getId(), incomingContainerIds).stream()
                                .collect(Collectors.toMap(ServiceReplica::getContainerId, r -> r));

                for (ServiceReplicaDTO replicaDto : serviceDto.getReplicas()) {
                    Host host = hostsByMachineId.get(replicaDto.getMachineId());
                    if (host == null) {
                        throw new BadRequestException("Invalid replica: machineId not found in hosts: " + replicaDto.getMachineId());
                    }

                    var existingReplica = existingReplicasByContainerId.get(replicaDto.getContainerId());
                    if (existingReplica != null) {
                        // Replica exists - check if it's soft-deleted
                        if (existingReplica.getDeletedAt() != null) {
                            existingReplica.setDeletedAt(null);
                            replicasReactivated++;
                            log.debug("Reactivating replica: containerId={}, replicaId={}", replicaDto.getContainerId(), existingReplica.getId());
                        } else {
                            replicasUpdated++;
                        }
                        // Update fields
                        existingReplica.setHost(host);
                        existingReplica.setContainerName(replicaDto.getContainerName());
                        existingReplica.setStartedAt(replicaDto.getStartedAt());
                        existingReplica.setLastSeenAt(now);
                        serviceReplicaRepository.save(existingReplica);
                    } else {
                        // Create new replica
                        var replica = new ServiceReplica();
                        replica.setService(service);
                        replica.setHost(host);
                        replica.setContainerId(replicaDto.getContainerId());
                        replica.setContainerName(replicaDto.getContainerName());
                        replica.setStartedAt(replicaDto.getStartedAt());
                        replica.setCreatedAt(now);
                        replica.setLastSeenAt(now);
                        serviceReplicaRepository.save(replica);
                        replicasCreated++;
                        log.debug("Created replica: containerId={}, containerName={}, replicaId={}", 
                                replicaDto.getContainerId(), replicaDto.getContainerName(), replica.getId());
                    }
                }

                // Soft-delete replicas not in payload (only active ones)
                List<ServiceReplica> replicasToSoftDelete = serviceReplicaRepository.findByServiceIdAndDeletedAtIsNull(service.getId()).stream()
                        .filter(r -> !incomingContainerIds.contains(r.getContainerId()))
                        .toList();

                if (!replicasToSoftDelete.isEmpty()) {
                    for (ServiceReplica replica : replicasToSoftDelete) {
                        replica.setDeletedAt(now);
                        replicasSoftDeleted++;
                        log.debug("Soft-deleting replica: containerId={}, replicaId={}", replica.getContainerId(), replica.getId());
                    }
                    serviceReplicaRepository.saveAll(replicasToSoftDelete);
                }
            }
        }

        // Step 6: Soft-delete services not in payload
        List<dk.viplev.api.domain.model.Service> servicesToSoftDelete = existingServicesByName.values().stream()
                .filter(s -> !incomingServiceNames.contains(s.getServiceName()))
                .filter(s -> s.getDeletedAt() == null) // Only soft-delete active services
                .toList();

        int servicesSoftDeleted = 0;

        for (dk.viplev.api.domain.model.Service service : servicesToSoftDelete) {
            service.setDeletedAt(now);
            serviceRepository.save(service);
            servicesSoftDeleted++;
            log.debug("Soft-deleting service: serviceName={}, serviceId={}", service.getServiceName(), service.getId());

            // Cascade soft-delete to all active replicas
            List<ServiceReplica> activeReplicas = serviceReplicaRepository.findByServiceIdAndDeletedAtIsNull(service.getId());
            if (!activeReplicas.isEmpty()) {
                for (ServiceReplica replica : activeReplicas) {
                    replica.setDeletedAt(now);
                    replicasSoftDeleted++;
                    log.debug("Soft-deleting replica: containerId={}, replicaId={}", replica.getContainerId(), replica.getId());
                }
                serviceReplicaRepository.saveAll(activeReplicas);
            }
        }

        log.info("Service registration completed: environmentId={}, hostCount={}, serviceCount={}, servicesCreated={}, servicesUpdated={}, servicesReactivated={}, servicesSoftDeleted={}, replicasCreated={}, replicasUpdated={}, replicasReactivated={}, replicasSoftDeleted={}",
                environmentId, hostCount, serviceCount, servicesCreated, servicesUpdated, servicesReactivated, servicesSoftDeleted, replicasCreated, replicasUpdated, replicasReactivated, replicasSoftDeleted);
    }

    private void validateRegistration(ServiceRegistrationDTO registration) {
        // Validate hosts
        Set<String> seenMachineIds = new HashSet<>();
        for (HostDTO hostDto : registration.getHosts()) {
            if (hostDto == null) {
                throw new BadRequestException("Invalid registration", "hosts must not contain null entries");
            }
            if (hostDto.getMachineId() == null || hostDto.getMachineId().isBlank()) {
                throw new BadRequestException("Invalid registration", "machineId must not be null or blank");
            }
            if (!seenMachineIds.add(hostDto.getMachineId())) {
                throw new BadRequestException("Duplicate machineId: " + hostDto.getMachineId());
            }
        }

        // Validate services
        Set<String> seenServiceNames = new HashSet<>();
        Set<String> globalContainerIds = new HashSet<>();
        for (ServiceRegistrationServiceDTO serviceDto : registration.getServices()) {
            if (serviceDto == null) {
                throw new BadRequestException("Invalid registration", "services must not contain null entries");
            }
            if (serviceDto.getServiceName() == null || serviceDto.getServiceName().isBlank()) {
                throw new BadRequestException("serviceName must not be null or blank");
            }
            if (!seenServiceNames.add(serviceDto.getServiceName())) {
                throw new BadRequestException("Duplicate serviceName: " + serviceDto.getServiceName());
            }
            if (serviceDto.getReplicas() == null) {
                throw new BadRequestException("Invalid registration", "replicas must not be null for service: " + serviceDto.getServiceName());
            }

            // Validate replicas
            for (ServiceReplicaDTO replicaDto : serviceDto.getReplicas()) {
                if (replicaDto == null) {
                    throw new BadRequestException("Invalid registration", "replicas must not contain null entries");
                }
                if (replicaDto.getContainerId() == null || replicaDto.getContainerId().isBlank()) {
                    throw new BadRequestException("containerId must not be null or blank");
                }
                if (replicaDto.getContainerName() == null || replicaDto.getContainerName().isBlank()) {
                    throw new BadRequestException("containerName must not be null or blank");
                }
                if (replicaDto.getMachineId() == null || replicaDto.getMachineId().isBlank()) {
                    throw new BadRequestException("machineId must not be null or blank for replica");
                }
                if (!seenMachineIds.contains(replicaDto.getMachineId())) {
                    throw new BadRequestException("Invalid replica: machineId not found in hosts: " + replicaDto.getMachineId());
                }
                // Global containerId uniqueness check (Docker container IDs are globally unique)
                if (!globalContainerIds.add(replicaDto.getContainerId())) {
                    throw new BadRequestException("Duplicate containerId across services: " + replicaDto.getContainerId());
                }
            }
        }
    }

    private Map<String, Host> upsertHosts(dk.viplev.api.domain.model.Environment environment, List<HostDTO> hostDtos) {
        Map<String, Host> hostsByMachineId = new java.util.HashMap<>();

        for (HostDTO hostDto : hostDtos) {
            Host host = hostRepository.findByEnvironmentIdAndMachineId(environment.getId(), hostDto.getMachineId())
                    .orElseGet(() -> {
                        Host h = new Host();
                        h.setEnvironment(environment);
                        h.setMachineId(hostDto.getMachineId());
                        return h;
                    });

            host.setName(hostDto.getName());
            host.setIpAddress(hostDto.getIpAddress());
            host.setOs(hostDto.getOs());
            host.setOsVersion(hostDto.getOsVersion());
            host.setCpuModel(hostDto.getCpuModel());
            host.setCpuCores(hostDto.getCpuCores());
            host.setCpuThreads(hostDto.getCpuThreads());
            host.setRamTotalBytes(hostDto.getRamTotalBytes());
            host.setRamSpeedMhz(hostDto.getRamSpeedMhz());
            host.setRamType(hostDto.getRamType());
            host = hostRepository.save(host);

            hostsByMachineId.put(hostDto.getMachineId(), host);
        }

        return hostsByMachineId;
    }

    private void verifyEnvironmentOwnership(UUID environmentId) {
        UUID ownerId = authService.getAuthenticatedUserId();
        environmentRepository.findByIdAndOwnerId(environmentId, ownerId)
                .orElseThrow(() -> new NotFoundException("Environment not found"));
    }
}
