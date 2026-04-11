package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.HostDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceRegistrationDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceRegistrationHostDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.ServiceMapper;
import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.Host;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.inbound.ServiceService;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;
import dk.viplev.api.port.outbound.db.HostRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

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
    private final HostRepository hostRepository;
    private final EnvironmentRepository environmentRepository;
    private final ServiceMapper serviceMapper;
    private final AuthService authService;

    @Override
    public List<ServiceDTO> listServices(UUID environmentId) {
        verifyEnvironmentOwnership(environmentId);
        return serviceMapper.toDtoList(serviceRepository.findByHostEnvironmentId(environmentId));
    }

    @Override
    public ServiceDTO getService(UUID environmentId, UUID serviceId) {
        verifyEnvironmentOwnership(environmentId);
        var service = serviceRepository.findByIdAndHostEnvironmentId(serviceId, environmentId)
                .orElseThrow(() -> new NotFoundException("Service not found"));
        return serviceMapper.toDto(service);
    }

    @Override
    @Transactional
    public void registerServices(UUID environmentId, ServiceRegistrationDTO registration) {
        int hostCount = registration != null && registration.getHosts() != null ? registration.getHosts().size() : 0;
        int serviceCount = countServices(registration);
        log.info("Registering services for environment: environmentId={}, hostCount={}, serviceCount={}",
                environmentId, hostCount, serviceCount);

        UUID agentEnvironmentId = authService.getAuthenticatedEnvironmentId();
        if (agentEnvironmentId == null || !agentEnvironmentId.equals(environmentId)) {
            throw new BadRequestException("Agent token does not match the environment");
        }

        if (registration.getHosts() == null) {
            throw new BadRequestException("Invalid registration", "hosts must not be null");
        }

        Set<String> seenMachineIds = new HashSet<>();
        for (ServiceRegistrationHostDTO hostEntry : registration.getHosts()) {
            if (hostEntry == null) {
                throw new BadRequestException("Invalid registration", "hosts must not contain null entries");
            }
            if (hostEntry.getHost() == null) {
                throw new BadRequestException("Invalid registration", "host must not be null for each hosts entry");
            }
            HostDTO hostDto = hostEntry.getHost();
            if (hostDto.getMachineId() == null || hostDto.getMachineId().isBlank()) {
                throw new BadRequestException("Invalid registration", "machineId must not be null or blank");
            }
            if (!seenMachineIds.add(hostDto.getMachineId())) {
                throw new BadRequestException("Duplicate machineId: " + hostDto.getMachineId());
            }
            if (hostEntry.getServices() == null) {
                throw new BadRequestException("Invalid registration", "services must not be null for each hosts entry");
            }
            validateServiceNames(hostEntry.getServices());
        }

        var environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new NotFoundException("Environment not found"));

        int servicesCreated = 0;
        int servicesUpdated = 0;
        int servicesDeleted = 0;

        for (ServiceRegistrationHostDTO hostEntry : registration.getHosts()) {

            HostDTO hostDto = hostEntry.getHost();
            Host host = hostRepository.findByEnvironmentIdAndMachineId(environmentId, hostDto.getMachineId())
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

            int hostIncomingServiceCount = hostEntry.getServices() != null ? hostEntry.getServices().size() : 0;

            Map<String, dk.viplev.api.domain.model.Service> existingByName =
                    serviceRepository.findByHostId(host.getId()).stream()
                            .collect(Collectors.toMap(dk.viplev.api.domain.model.Service::getServiceName, s -> s));

            Set<String> incomingServiceNames = new HashSet<>();
            int hostServicesCreated = 0;
            int hostServicesUpdated = 0;
            for (ServiceDTO dto : hostEntry.getServices()) {
                incomingServiceNames.add(dto.getServiceName());

                var existing = existingByName.get(dto.getServiceName());
                if (existing != null) {
                    existing.setImageSha(dto.getImageSha());
                    existing.setImageName(dto.getImageName());
                    existing.setCpuLimit(dto.getCpuLimit());
                    existing.setCpuReservation(dto.getCpuReservation());
                    existing.setMemoryLimitBytes(dto.getMemoryLimitBytes());
                    existing.setMemoryReservationBytes(dto.getMemoryReservationBytes());
                    serviceRepository.save(existing);
                    hostServicesUpdated++;
                    servicesUpdated++;
                } else {
                    var svc = new dk.viplev.api.domain.model.Service();
                    svc.setHost(host);
                    svc.setServiceName(dto.getServiceName());
                    svc.setImageSha(dto.getImageSha());
                    svc.setImageName(dto.getImageName());
                    svc.setCpuLimit(dto.getCpuLimit());
                    svc.setCpuReservation(dto.getCpuReservation());
                    svc.setMemoryLimitBytes(dto.getMemoryLimitBytes());
                    svc.setMemoryReservationBytes(dto.getMemoryReservationBytes());
                    serviceRepository.save(svc);
                    hostServicesCreated++;
                    servicesCreated++;
                }
            }

            List<dk.viplev.api.domain.model.Service> servicesToDelete = existingByName.values().stream()
                    .filter(s -> !incomingServiceNames.contains(s.getServiceName()))
                    .toList();
            servicesToDelete.forEach(serviceRepository::delete);
            servicesDeleted += servicesToDelete.size();

            log.info("Registered services for host: environmentId={}, machineId={}, incomingServiceCount={}, created={}, updated={}, deleted={}",
                    environmentId,
                    hostDto.getMachineId(),
                    hostIncomingServiceCount,
                    hostServicesCreated,
                    hostServicesUpdated,
                    servicesToDelete.size());
        }

        log.info("Service registration completed: environmentId={}, hostCount={}, serviceCount={}, servicesCreated={}, servicesUpdated={}, servicesDeleted={}",
                environmentId, registration.getHosts().size(), serviceCount, servicesCreated, servicesUpdated, servicesDeleted);
    }

    private int countServices(ServiceRegistrationDTO registration) {
        if (registration == null || registration.getHosts() == null) {
            return 0;
        }

        int count = 0;
        for (ServiceRegistrationHostDTO hostEntry : registration.getHosts()) {
            if (hostEntry != null && hostEntry.getServices() != null) {
                count += hostEntry.getServices().size();
            }
        }
        return count;
    }

    private void validateServiceNames(List<ServiceDTO> services) {
        Set<String> seen = new HashSet<>();
        for (ServiceDTO dto : services) {
            if (dto == null) {
                throw new BadRequestException("Invalid registration", "services must not contain null entries");
            }
            if (dto.getServiceName() == null || dto.getServiceName().isBlank()) {
                throw new BadRequestException("serviceName must not be null or blank");
            }
            if (!seen.add(dto.getServiceName())) {
                throw new BadRequestException("Duplicate serviceName: " + dto.getServiceName());
            }
        }
    }

    private void verifyEnvironmentOwnership(UUID environmentId) {
        UUID ownerId = authService.getAuthenticatedUserId();
        environmentRepository.findByIdAndOwnerId(environmentId, ownerId)
                .orElseThrow(() -> new NotFoundException("Environment not found"));
    }
}
