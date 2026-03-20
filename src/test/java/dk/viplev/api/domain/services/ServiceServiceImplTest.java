package dk.viplev.api.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dk.viplev.api.adapter.inbound.rest.dto.HostDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceRegistrationDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.ServiceMapper;
import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.domain.model.Host;
import dk.viplev.api.domain.model.Service;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;
import dk.viplev.api.port.outbound.db.HostRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ServiceServiceImplTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private HostRepository hostRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private AuthService authService;

    private final ServiceMapper serviceMapper = Mappers.getMapper(ServiceMapper.class);

    private ServiceServiceImpl serviceService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID environmentId = UUID.randomUUID();
    private Environment environment;
    private Host host;

    @BeforeEach
    void setUp() {
        serviceService = new ServiceServiceImpl(
                serviceRepository, hostRepository, environmentRepository,
                serviceMapper, authService);

        User owner = new User();
        owner.setId(ownerId);

        environment = new Environment();
        environment.setId(environmentId);
        environment.setOwner(owner);

        host = new Host();
        host.setId(UUID.randomUUID());
        host.setEnvironment(environment);
        host.setName("test-host");
        host.setMachineId("abc123");
    }

    private ServiceRegistrationDTO buildRegistration(List<ServiceDTO> services) {
        HostDTO hostDto = new HostDTO();
        hostDto.setName("test-host");
        hostDto.setMachineId("abc123");
        hostDto.setOs("Linux");
        hostDto.setIpAddress("192.168.1.100");

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHost(hostDto);
        reg.setServices(services);
        return reg;
    }

    private void mockAgentAuth() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "abc123"))
                .thenReturn(Optional.of(host));
        when(hostRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldListServicesForEnvironment() {
        when(authService.getAuthenticatedUserId()).thenReturn(ownerId);
        when(environmentRepository.findByIdAndOwnerId(environmentId, ownerId))
                .thenReturn(Optional.of(environment));

        Service svc = createService("my-service", "nginx:latest");
        when(serviceRepository.findByHostEnvironmentId(environmentId)).thenReturn(List.of(svc));

        List<ServiceDTO> result = serviceService.listServices(environmentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getServiceName()).isEqualTo("my-service");
        assertThat(result.get(0).getImageName()).isEqualTo("nginx:latest");
    }

    @Test
    void shouldGetServiceById() {
        when(authService.getAuthenticatedUserId()).thenReturn(ownerId);
        when(environmentRepository.findByIdAndOwnerId(environmentId, ownerId))
                .thenReturn(Optional.of(environment));

        Service svc = createService("my-service", "nginx:latest");
        when(serviceRepository.findByIdAndHostEnvironmentId(svc.getId(), environmentId))
                .thenReturn(Optional.of(svc));

        ServiceDTO result = serviceService.getService(environmentId, svc.getId());

        assertThat(result.getServiceName()).isEqualTo("my-service");
    }

    @Test
    void shouldThrowNotFoundWhenServiceDoesNotExist() {
        when(authService.getAuthenticatedUserId()).thenReturn(ownerId);
        when(environmentRepository.findByIdAndOwnerId(environmentId, ownerId))
                .thenReturn(Optional.of(environment));

        UUID serviceId = UUID.randomUUID();
        when(serviceRepository.findByIdAndHostEnvironmentId(serviceId, environmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceService.getService(environmentId, serviceId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Service not found");
    }

    @Test
    void shouldThrowNotFoundWhenEnvironmentNotOwned() {
        when(authService.getAuthenticatedUserId()).thenReturn(ownerId);
        when(environmentRepository.findByIdAndOwnerId(environmentId, ownerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceService.listServices(environmentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Environment not found");
    }

    @Test
    void shouldRegisterNewServices() {
        mockAgentAuth();
        when(serviceRepository.findByHostId(host.getId())).thenReturn(new ArrayList<>());
        when(serviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceDTO dto = new ServiceDTO();
        dto.setServiceName("new-service");
        dto.setImageName("nginx:latest");

        serviceService.registerServices(environmentId, buildRegistration(List.of(dto)));

        verify(serviceRepository).save(any());
    }

    @Test
    void shouldUpdateExistingServices() {
        mockAgentAuth();

        Service existing = createService("my-service", "nginx:1.0");
        when(serviceRepository.findByHostId(host.getId()))
                .thenReturn(new ArrayList<>(List.of(existing)));
        when(serviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceDTO dto = new ServiceDTO();
        dto.setServiceName("my-service");
        dto.setImageName("nginx:2.0");

        serviceService.registerServices(environmentId, buildRegistration(List.of(dto)));

        verify(serviceRepository).save(any());
        assertThat(existing.getImageName()).isEqualTo("nginx:2.0");
    }

    @Test
    void shouldDeleteRemovedServices() {
        mockAgentAuth();

        Service existing = createService("old-service", "nginx:1.0");
        when(serviceRepository.findByHostId(host.getId()))
                .thenReturn(new ArrayList<>(List.of(existing)));

        serviceService.registerServices(environmentId, buildRegistration(List.of()));

        verify(serviceRepository).delete(existing);
    }

    @Test
    void shouldRejectAgentWithMismatchedEnvironment() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, buildRegistration(List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Agent token does not match the environment");
    }

    @Test
    void shouldRejectNonAgentCallingRegisterServices() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(null);

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, buildRegistration(List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Agent token does not match the environment");
    }

    @Test
    void shouldRejectNullServiceName() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        ServiceDTO dto = new ServiceDTO();
        dto.setImageName("nginx:latest");

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, buildRegistration(List.of(dto))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("serviceName must not be null or blank");
    }

    @Test
    void shouldRejectDuplicateServiceNames() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        ServiceDTO dto1 = new ServiceDTO();
        dto1.setServiceName("same-name");
        dto1.setImageName("nginx:latest");

        ServiceDTO dto2 = new ServiceDTO();
        dto2.setServiceName("same-name");
        dto2.setImageName("redis:7");

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, buildRegistration(List.of(dto1, dto2))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Duplicate serviceName: same-name");
    }

    @Test
    void shouldCreateHostWhenNotExists() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "abc123"))
                .thenReturn(Optional.empty());
        when(hostRepository.save(any())).thenAnswer(inv -> {
            Host h = inv.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(serviceRepository.findByHostId(any())).thenReturn(new ArrayList<>());
        when(serviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceDTO dto = new ServiceDTO();
        dto.setServiceName("new-service");
        dto.setImageName("nginx:latest");

        serviceService.registerServices(environmentId, buildRegistration(List.of(dto)));

        verify(hostRepository).save(any());
        verify(serviceRepository).save(any());
    }

    private Service createService(String name, String imageName) {
        Service svc = new Service();
        svc.setId(UUID.randomUUID());
        svc.setHost(host);
        svc.setServiceName(name);
        svc.setImageName(imageName);
        return svc;
    }
}
