package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.ServiceDTO;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    public void registerServices(UUID environmentId, List<ServiceDTO> services) {
        UUID agentEnvironmentId = authService.getAuthenticatedEnvironmentId();
        if (agentEnvironmentId == null || !agentEnvironmentId.equals(environmentId)) {
            throw new BadRequestException("Agent token does not match the environment");
        }

        List<Host> hosts = hostRepository.findByEnvironmentId(environmentId);
        if (hosts.isEmpty()) {
            throw new NotFoundException("No hosts found for environment");
        }
        Host host = hosts.get(0);

        List<dk.viplev.api.domain.model.Service> existingServices =
                serviceRepository.findByHostEnvironmentId(environmentId);

        Set<String> incomingServiceNames = new HashSet<>();
        for (ServiceDTO dto : services) {
            incomingServiceNames.add(dto.getServiceName());

            var existing = existingServices.stream()
                    .filter(s -> s.getServiceName().equals(dto.getServiceName()))
                    .findFirst();

            if (existing.isPresent()) {
                var svc = existing.get();
                svc.setImageSha(dto.getImageSha());
                svc.setImageName(dto.getImageName());
                svc.setCpuLimit(dto.getCpuLimit());
                svc.setCpuReservation(dto.getCpuReservation());
                svc.setMemoryLimitBytes(dto.getMemoryLimitBytes());
                svc.setMemoryReservationBytes(dto.getMemoryReservationBytes());
                serviceRepository.save(svc);
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
            }
        }

        // Delete services no longer present
        existingServices.stream()
                .filter(s -> !incomingServiceNames.contains(s.getServiceName()))
                .forEach(serviceRepository::delete);
    }

    private void verifyEnvironmentOwnership(UUID environmentId) {
        UUID ownerId = authService.getAuthenticatedUserId();
        environmentRepository.findByIdAndOwnerId(environmentId, ownerId)
                .orElseThrow(() -> new NotFoundException("Environment not found"));
    }
}
