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
import dk.viplev.api.adapter.inbound.rest.dto.ServiceRegistrationHostDTO;
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
        return buildRegistrationForHost("abc123", "test-host", services);
    }

    private ServiceRegistrationDTO buildRegistrationForHost(String machineId, String hostName, List<ServiceDTO> services) {
        HostDTO hostDto = new HostDTO();
        hostDto.setName(hostName);
        hostDto.setMachineId(machineId);
        hostDto.setOs("Linux");
        hostDto.setIpAddress("192.168.1.100");

        ServiceRegistrationHostDTO hostEntry = new ServiceRegistrationHostDTO();
        hostEntry.setHost(hostDto);
        hostEntry.setServices(services);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHosts(List.of(hostEntry));
        return reg;
    }

    private ServiceRegistrationDTO buildMultiHostRegistration(
            String machineId1, List<ServiceDTO> services1,
            String machineId2, List<ServiceDTO> services2) {
        HostDTO hostDto1 = new HostDTO();
        hostDto1.setName("host-" + machineId1);
        hostDto1.setMachineId(machineId1);
        hostDto1.setOs("Linux");
        hostDto1.setIpAddress("192.168.1.100");

        ServiceRegistrationHostDTO hostEntry1 = new ServiceRegistrationHostDTO();
        hostEntry1.setHost(hostDto1);
        hostEntry1.setServices(services1);

        HostDTO hostDto2 = new HostDTO();
        hostDto2.setName("host-" + machineId2);
        hostDto2.setMachineId(machineId2);
        hostDto2.setOs("Linux");
        hostDto2.setIpAddress("192.168.1.101");

        ServiceRegistrationHostDTO hostEntry2 = new ServiceRegistrationHostDTO();
        hostEntry2.setHost(hostDto2);
        hostEntry2.setServices(services2);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHosts(List.of(hostEntry1, hostEntry2));
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

    @Test
    void shouldRegisterServicesForMultipleHosts() {
        UUID host2Id = UUID.randomUUID();
        Host host2 = new Host();
        host2.setId(host2Id);
        host2.setEnvironment(environment);
        host2.setName("host-node2");
        host2.setMachineId("node2");

        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "abc123"))
                .thenReturn(Optional.of(host));
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "node2"))
                .thenReturn(Optional.of(host2));
        when(hostRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceRepository.findByHostId(host.getId())).thenReturn(new ArrayList<>());
        when(serviceRepository.findByHostId(host2Id)).thenReturn(new ArrayList<>());
        when(serviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceDTO dto1 = new ServiceDTO();
        dto1.setServiceName("service-on-host1");
        dto1.setImageName("nginx:latest");

        ServiceDTO dto2 = new ServiceDTO();
        dto2.setServiceName("service-on-host2");
        dto2.setImageName("redis:7");

        serviceService.registerServices(environmentId, buildMultiHostRegistration(
                "abc123", List.of(dto1),
                "node2", List.of(dto2)));

        // save called once per host (2 hosts) + once per service (2 services) = but hostRepository.save called twice
        verify(hostRepository, org.mockito.Mockito.times(2)).save(any());
        verify(serviceRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void shouldRejectDuplicateServiceNamesPerHost() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        ServiceDTO dto1 = new ServiceDTO();
        dto1.setServiceName("duplicate-name");

        ServiceDTO dto2 = new ServiceDTO();
        dto2.setServiceName("duplicate-name");

        assertThatThrownBy(() -> serviceService.registerServices(environmentId,
                buildRegistration(List.of(dto1, dto2))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Duplicate serviceName: duplicate-name");
    }

    @Test
    void shouldRejectNullHosts() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHosts(null);

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectNullHostInEntry() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        ServiceRegistrationHostDTO hostEntry = new ServiceRegistrationHostDTO();
        hostEntry.setHost(null);
        hostEntry.setServices(List.of());

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHosts(List.of(hostEntry));

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectNullServicesInEntry() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        HostDTO hostDto = new HostDTO();
        hostDto.setName("test-host");
        hostDto.setMachineId("abc123");

        ServiceRegistrationHostDTO hostEntry = new ServiceRegistrationHostDTO();
        hostEntry.setHost(hostDto);
        hostEntry.setServices(null);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHosts(List.of(hostEntry));

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectNullHostEntry() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        ArrayList<ServiceRegistrationHostDTO> hosts = new ArrayList<>();
        hosts.add(null);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHosts(hosts);

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectNullServiceDTOInServicesList() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        HostDTO hostDto = new HostDTO();
        hostDto.setName("test-host");
        hostDto.setMachineId("abc123");

        ArrayList<ServiceDTO> services = new ArrayList<>();
        services.add(null);

        ServiceRegistrationHostDTO hostEntry = new ServiceRegistrationHostDTO();
        hostEntry.setHost(hostDto);
        hostEntry.setServices(services);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHosts(List.of(hostEntry));

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectNullMachineId() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        HostDTO hostDto = new HostDTO();
        hostDto.setName("test-host");
        hostDto.setMachineId(null);

        ServiceRegistrationHostDTO hostEntry = new ServiceRegistrationHostDTO();
        hostEntry.setHost(hostDto);
        hostEntry.setServices(List.of());

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHosts(List.of(hostEntry));

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectBlankMachineId() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        HostDTO hostDto = new HostDTO();
        hostDto.setName("test-host");
        hostDto.setMachineId("   ");

        ServiceRegistrationHostDTO hostEntry = new ServiceRegistrationHostDTO();
        hostEntry.setHost(hostDto);
        hostEntry.setServices(List.of());

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHosts(List.of(hostEntry));

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectDuplicateMachineIds() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        HostDTO hostDto1 = new HostDTO();
        hostDto1.setName("host-1");
        hostDto1.setMachineId("abc123");

        ServiceRegistrationHostDTO hostEntry1 = new ServiceRegistrationHostDTO();
        hostEntry1.setHost(hostDto1);
        hostEntry1.setServices(List.of());

        HostDTO hostDto2 = new HostDTO();
        hostDto2.setName("host-2");
        hostDto2.setMachineId("abc123");

        ServiceRegistrationHostDTO hostEntry2 = new ServiceRegistrationHostDTO();
        hostEntry2.setHost(hostDto2);
        hostEntry2.setServices(List.of());

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setHosts(List.of(hostEntry1, hostEntry2));

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate machineId: abc123");
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
