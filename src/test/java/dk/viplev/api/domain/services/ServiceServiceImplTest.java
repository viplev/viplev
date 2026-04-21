package dk.viplev.api.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dk.viplev.api.adapter.inbound.rest.dto.HostDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceRegistrationDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceRegistrationServiceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceReplicaDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceDTO;
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
import dk.viplev.api.port.outbound.db.ServiceReplicaRepository;
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
    private ServiceReplicaRepository serviceReplicaRepository;

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
                serviceRepository, serviceReplicaRepository, hostRepository, environmentRepository,
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

    private ServiceRegistrationDTO buildRegistration(List<ServiceRegistrationServiceDTO> services) {
        return buildRegistrationForHost("abc123", "test-host", services);
    }

    private ServiceRegistrationDTO buildRegistrationForHost(String machineId, String hostName, List<ServiceRegistrationServiceDTO> services) {
        HostDTO hostDto = new HostDTO();
        hostDto.setName(hostName);
        hostDto.setMachineId(machineId);
        hostDto.setOs("Linux");
        hostDto.setIpAddress("192.168.1.100");

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setServices(services);
        reg.setHosts(List.of(hostDto));
        return reg;
    }

    private ServiceRegistrationDTO buildMultiHostRegistration(
            String machineId1, String machineId2, List<ServiceRegistrationServiceDTO> services) {
        HostDTO hostDto1 = new HostDTO();
        hostDto1.setName("host-" + machineId1);
        hostDto1.setMachineId(machineId1);
        hostDto1.setOs("Linux");
        hostDto1.setIpAddress("192.168.1.100");

        HostDTO hostDto2 = new HostDTO();
        hostDto2.setName("host-" + machineId2);
        hostDto2.setMachineId(machineId2);
        hostDto2.setOs("Linux");
        hostDto2.setIpAddress("192.168.1.101");

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setServices(services);
        reg.setHosts(List.of(hostDto1, hostDto2));
        return reg;
    }

    private ServiceRegistrationServiceDTO buildServiceWithReplica(String serviceName, String imageName, String containerId, String containerName, String machineId) {
        ServiceReplicaDTO replica = new ServiceReplicaDTO();
        replica.setContainerId(containerId);
        replica.setContainerName(containerName);
        replica.setMachineId(machineId);

        ServiceRegistrationServiceDTO service = new ServiceRegistrationServiceDTO();
        service.setServiceName(serviceName);
        service.setImageName(imageName);
        service.setReplicas(List.of(replica));
        return service;
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
        when(serviceRepository.findByEnvironmentIdAndDeletedAtIsNull(environmentId)).thenReturn(List.of(svc));

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
        when(serviceRepository.findByIdAndEnvironmentId(svc.getId(), environmentId))
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
        when(serviceRepository.findByIdAndEnvironmentId(serviceId, environmentId))
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
        when(serviceRepository.findByEnvironmentId(environmentId)).thenReturn(new ArrayList<>());
        when(serviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceReplicaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceRegistrationServiceDTO service = buildServiceWithReplica("new-service", "nginx:latest", "container123", "new-service-1", "abc123");

        serviceService.registerServices(environmentId, buildRegistration(List.of(service)));

        verify(serviceRepository).save(any());
        verify(serviceReplicaRepository).save(any());
    }

    @Test
    void shouldUpdateExistingServices() {
        mockAgentAuth();

        Service existing = createService("my-service", "nginx:1.0");
        when(serviceRepository.findByEnvironmentId(environmentId))
                .thenReturn(new ArrayList<>(List.of(existing)));
        when(serviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceReplicaRepository.findByServiceIdAndContainerIdIn(any(), any())).thenReturn(List.of());
        when(serviceReplicaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceReplicaRepository.findByServiceIdAndDeletedAtIsNull(any())).thenReturn(List.of());

        ServiceRegistrationServiceDTO service = buildServiceWithReplica("my-service", "nginx:2.0", "container123", "my-service-1", "abc123");

        serviceService.registerServices(environmentId, buildRegistration(List.of(service)));

        verify(serviceRepository).save(any());
        assertThat(existing.getImageName()).isEqualTo("nginx:2.0");
    }

    @Test
    void shouldDeleteRemovedServices() {
        mockAgentAuth();

        Service existing = createService("old-service", "nginx:1.0");
        when(serviceRepository.findByEnvironmentId(environmentId))
                .thenReturn(new ArrayList<>(List.of(existing)));
        when(serviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceReplicaRepository.findByServiceIdAndDeletedAtIsNull(any())).thenReturn(List.of());

        serviceService.registerServices(environmentId, buildRegistration(List.of()));

        // Should soft-delete, not hard-delete
        verify(serviceRepository, never()).delete(existing);
        verify(serviceRepository).save(existing);
        assertThat(existing.getDeletedAt()).isNotNull();
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
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        ServiceRegistrationServiceDTO service = new ServiceRegistrationServiceDTO();
        service.setImageName("nginx:latest");
        service.setReplicas(List.of());

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, buildRegistration(List.of(service))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("serviceName must not be null or blank");
    }

    @Test
    void shouldRejectDuplicateServiceNames() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        ServiceRegistrationServiceDTO service1 = buildServiceWithReplica("same-name", "nginx:latest", "c1", "c1", "abc123");
        ServiceRegistrationServiceDTO service2 = buildServiceWithReplica("same-name", "redis:7", "c2", "c2", "abc123");

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, buildRegistration(List.of(service1, service2))))
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
        when(serviceRepository.findByEnvironmentId(any())).thenReturn(new ArrayList<>());
        when(serviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceReplicaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceReplicaRepository.findByServiceIdAndContainerIdIn(any(), any())).thenReturn(List.of());
        when(serviceReplicaRepository.findByServiceIdAndDeletedAtIsNull(any())).thenReturn(List.of());

        ServiceRegistrationServiceDTO service = buildServiceWithReplica("new-service", "nginx:latest", "c1", "c1", "abc123");

        serviceService.registerServices(environmentId, buildRegistration(List.of(service)));

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
        when(serviceRepository.findByEnvironmentId(environmentId)).thenReturn(new ArrayList<>());
        when(serviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceReplicaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceReplicaRepository.findByServiceIdAndContainerIdIn(any(), any())).thenReturn(List.of());
        when(serviceReplicaRepository.findByServiceIdAndDeletedAtIsNull(any())).thenReturn(List.of());

        ServiceRegistrationServiceDTO service1 = buildServiceWithReplica("service-on-host1", "nginx:latest", "c1", "c1", "abc123");
        ServiceRegistrationServiceDTO service2 = buildServiceWithReplica("service-on-host2", "redis:7", "c2", "c2", "node2");

        serviceService.registerServices(environmentId, buildMultiHostRegistration(
                "abc123", "node2", List.of(service1, service2)));

        verify(hostRepository, org.mockito.Mockito.times(2)).save(any());
        verify(serviceRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void shouldRejectDuplicateServiceNamesPerHost() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        ServiceRegistrationServiceDTO service1 = buildServiceWithReplica("duplicate-name", "nginx:latest", "c1", "c1", "abc123");
        ServiceRegistrationServiceDTO service2 = buildServiceWithReplica("duplicate-name", "redis:7", "c2", "c2", "abc123");

        assertThatThrownBy(() -> serviceService.registerServices(environmentId,
                buildRegistration(List.of(service1, service2))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Duplicate serviceName: duplicate-name");
    }

    @Test
    void shouldRejectNullHosts() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setServices(List.of());
        reg.setHosts(null);

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectNullServices() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setServices(null);
        reg.setHosts(List.of());

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectNullHostEntry() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        ArrayList<HostDTO> hosts = new ArrayList<>();
        hosts.add(null);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setServices(List.of());
        reg.setHosts(hosts);

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectNullServiceDTOInServicesList() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        ArrayList<ServiceRegistrationServiceDTO> services = new ArrayList<>();
        services.add(null);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setServices(services);
        reg.setHosts(List.of());

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectNullMachineId() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        HostDTO hostDto = new HostDTO();
        hostDto.setName("test-host");
        hostDto.setMachineId(null);

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setServices(List.of());
        reg.setHosts(List.of(hostDto));

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectBlankMachineId() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        HostDTO hostDto = new HostDTO();
        hostDto.setName("test-host");
        hostDto.setMachineId("   ");

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setServices(List.of());
        reg.setHosts(List.of(hostDto));

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid registration");
    }

    @Test
    void shouldRejectDuplicateMachineIds() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        HostDTO hostDto1 = new HostDTO();
        hostDto1.setName("host-1");
        hostDto1.setMachineId("abc123");

        HostDTO hostDto2 = new HostDTO();
        hostDto2.setName("host-2");
        hostDto2.setMachineId("abc123");

        ServiceRegistrationDTO reg = new ServiceRegistrationDTO();
        reg.setServices(List.of());
        reg.setHosts(List.of(hostDto1, hostDto2));

        assertThatThrownBy(() -> serviceService.registerServices(environmentId, reg))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate machineId: abc123");
    }

    @Test
    void shouldRejectDuplicateContainerIdsAcrossServices() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        // Two different services trying to use the same containerId (which is invalid - Docker container IDs are globally unique)
        ServiceRegistrationServiceDTO service1 = buildServiceWithReplica("nginx", "nginx:latest", "duplicate-container-id", "nginx-1", "abc123");
        ServiceRegistrationServiceDTO service2 = buildServiceWithReplica("redis", "redis:7", "duplicate-container-id", "redis-1", "abc123");

        assertThatThrownBy(() -> serviceService.registerServices(environmentId,
                buildRegistration(List.of(service1, service2))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate containerId across services: duplicate-container-id");
    }

    private Service createService(String name, String imageName) {
        Service svc = new Service();
        svc.setId(UUID.randomUUID());
        svc.setEnvironment(environment);
        svc.setServiceName(name);
        svc.setImageName(imageName);
        return svc;
    }
}
