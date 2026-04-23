package dk.viplev.api.domain.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

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
import dk.viplev.api.domain.model.Host;
import dk.viplev.api.domain.model.Service;
import dk.viplev.api.domain.model.ServiceReplica;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock private BenchmarkRunRepository benchmarkRunRepository;
    @Mock private BenchmarkRepository benchmarkRepository;
    @Mock private HostRepository hostRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private ServiceReplicaRepository serviceReplicaRepository;
    @Mock private MetricResourceHostRepository metricResourceHostRepository;
    @Mock private MetricResourceReplicaRepository metricResourceReplicaRepository;
    @Mock private MetricK6HttpRepository metricK6HttpRepository;
    @Mock private MetricK6VusRepository metricK6VusRepository;
    @Mock private EnvironmentRepository environmentRepository;
    @Mock private AuthService authService;
    @Mock private BenchmarkMapper benchmarkMapper;
    @Mock private BenchmarkRunMapper benchmarkRunMapper;
    @Mock private MessageMapper messageMapper;
    @Mock private BenchmarkServiceScopeService benchmarkServiceScopeService;

    private AgentServiceImpl agentService;

    private final UUID environmentId = UUID.randomUUID();
    private final UUID benchmarkId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();

    private BenchmarkRun activeRun;

    @BeforeEach
    void setUp() {
        agentService = new AgentServiceImpl(
                benchmarkRunRepository, benchmarkRepository, hostRepository, serviceRepository,
                serviceReplicaRepository,
                metricResourceHostRepository,
                metricResourceReplicaRepository,
                metricK6HttpRepository, metricK6VusRepository,
                environmentRepository,
                authService, benchmarkMapper, benchmarkRunMapper, messageMapper, benchmarkServiceScopeService);

        lenient().when(benchmarkServiceScopeService.getActiveScopedServiceIds(benchmarkId)).thenReturn(List.of());

        activeRun = new BenchmarkRun();
        activeRun.setId(runId);
        activeRun.setStatus(BenchmarkRunStatus.STARTED);
    }

    private void mockValidAccess() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(benchmarkRepository.findByIdAndEnvironmentId(benchmarkId, environmentId))
                .thenReturn(Optional.of(new dk.viplev.api.domain.model.Benchmark()));
        when(benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId))
                .thenReturn(Optional.of(activeRun));
    }

    private MetricDataPointDTO buildDataPoint() {
        MetricDataPointDTO dp = new MetricDataPointDTO();
        dp.setCollectedAt(LocalDateTime.now());
        dp.setCpuPercentage(10.0);
        dp.setMemoryUsageBytes(512L);
        dp.setMemoryLimitBytes(1024L);
        return dp;
    }

    private Host buildHost(String machineId) {
        Host host = new Host();
        host.setId(UUID.randomUUID());
        host.setMachineId(machineId);
        return host;
    }

    @Test
    void shouldStoreResourceMetricsForSingleNode() {
        mockValidAccess();
        Host host = buildHost("machine-1");
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-1"))
                .thenReturn(Optional.of(host));
        when(metricResourceHostRepository.saveAll(any())).thenReturn(List.of());

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(List.of(buildDataPoint()));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto);

        verify(metricResourceHostRepository).saveAll(any());
    }

    @Test
    void shouldStoreResourceMetricsForMultipleNodes() {
        mockValidAccess();
        Host host1 = buildHost("machine-1");
        Host host2 = buildHost("machine-2");
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-1"))
                .thenReturn(Optional.of(host1));
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-2"))
                .thenReturn(Optional.of(host2));
        when(metricResourceHostRepository.saveAll(any())).thenReturn(List.of());

        MetricResourceNodeDTO node1 = new MetricResourceNodeDTO();
        node1.setMachineId("machine-1");
        node1.setMetrics(List.of(buildDataPoint()));

        MetricResourceNodeDTO node2 = new MetricResourceNodeDTO();
        node2.setMachineId("machine-2");
        node2.setMetrics(List.of(buildDataPoint(), buildDataPoint()));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node1, node2));

        agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto);

        verify(metricResourceHostRepository, times(2)).saveAll(any());
    }

    @Test
    void shouldStoreServiceMetricsForNode() {
        mockValidAccess();
        Host host = buildHost("machine-1");
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-1"))
                .thenReturn(Optional.of(host));
        when(metricResourceHostRepository.saveAll(any())).thenReturn(List.of());

        Service svc = new Service();
        svc.setId(UUID.randomUUID());
        svc.setServiceName("my-service");
        when(benchmarkServiceScopeService.getActiveScopedServiceIds(benchmarkId)).thenReturn(List.of(svc.getId()));
        when(serviceRepository.findByEnvironmentIdAndServiceNameInAndDeletedAtIsNull(eq(environmentId), anySet()))
                .thenReturn(List.of(svc));
        
        // Mock replica lookup (returns empty, so a new one will be created)
        when(serviceReplicaRepository.findByServiceIdAndContainerId(eq(svc.getId()), eq("container-123")))
                .thenReturn(Optional.empty());
        
        // Mock replica save
        ServiceReplica savedReplica = new ServiceReplica();
        savedReplica.setId(UUID.randomUUID());
        when(serviceReplicaRepository.save(any())).thenReturn(savedReplica);
        when(serviceReplicaRepository.saveAll(any())).thenReturn(List.of(savedReplica));
        when(metricResourceReplicaRepository.saveAll(any())).thenReturn(List.of());

        MetricResourceServiceReplicaDTO replicaDto = new MetricResourceServiceReplicaDTO();
        replicaDto.setContainerId("container-123");
        replicaDto.setMetrics(List.of(buildDataPoint()));

        MetricResourceServiceDTO serviceDto = new MetricResourceServiceDTO();
        serviceDto.setServiceName("my-service");
        serviceDto.setReplicas(List.of(replicaDto));

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(List.of(buildDataPoint()));
        node.setServices(List.of(serviceDto));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto);

        verify(metricResourceHostRepository).saveAll(any());
        verify(metricResourceReplicaRepository).saveAll(any());
    }

    @Test
    void shouldThrowWhenHostNotFound() {
        mockValidAccess();
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "unknown-machine"))
                .thenReturn(Optional.empty());

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("unknown-machine");
        node.setMetrics(List.of(buildDataPoint()));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Host not found");
    }

    @Test
    void shouldThrowWhenServiceNotFoundOnHost() {
        mockValidAccess();
        Host host = buildHost("machine-1");
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-1"))
                .thenReturn(Optional.of(host));
        when(metricResourceHostRepository.saveAll(any())).thenReturn(List.of());
        when(serviceRepository.findByEnvironmentIdAndServiceNameInAndDeletedAtIsNull(eq(environmentId), anySet()))
                .thenReturn(List.of());

        MetricResourceServiceReplicaDTO replicaDto = new MetricResourceServiceReplicaDTO();
        replicaDto.setContainerId("container-123");
        replicaDto.setMetrics(List.of(buildDataPoint()));

        MetricResourceServiceDTO serviceDto = new MetricResourceServiceDTO();
        serviceDto.setServiceName("missing-service");
        serviceDto.setReplicas(List.of(replicaDto));

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(List.of(buildDataPoint()));
        node.setServices(List.of(serviceDto));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Service not found");
    }

    @Test
    void shouldThrowWhenRunIsNotActive() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(environmentId);
        when(benchmarkRepository.findByIdAndEnvironmentId(benchmarkId, environmentId))
                .thenReturn(Optional.of(new dk.viplev.api.domain.model.Benchmark()));
        activeRun.setStatus(BenchmarkRunStatus.FINISHED);
        when(benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId))
                .thenReturn(Optional.of(activeRun));

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(List.of(buildDataPoint()));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Run is not active");
    }

    @Test
    void shouldThrowWhenEnvironmentAccessDenied() {
        when(authService.getAuthenticatedEnvironmentId()).thenReturn(UUID.randomUUID());

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of());

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Environment not found");
    }

    @Test
    void shouldThrowWhenHostsIsNull() {
        mockValidAccess();

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(null);

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }

    @Test
    void shouldThrowWhenMachineIdIsNull() {
        mockValidAccess();

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId(null);
        node.setMetrics(List.of(buildDataPoint()));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }

    @Test
    void shouldThrowWhenMachineIdIsBlank() {
        mockValidAccess();

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("   ");
        node.setMetrics(List.of(buildDataPoint()));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }

    @Test
    void shouldThrowWhenNodeMetricsIsNull() {
        mockValidAccess();

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(null);

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }

    @Test
    void shouldThrowWhenServiceNameIsNull() {
        mockValidAccess();
        Host host = buildHost("machine-1");
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-1"))
                .thenReturn(Optional.of(host));
        when(metricResourceHostRepository.saveAll(any())).thenReturn(List.of());

        MetricResourceServiceReplicaDTO replicaDto = new MetricResourceServiceReplicaDTO();
        replicaDto.setContainerId("container-123");
        replicaDto.setMetrics(List.of(buildDataPoint()));

        MetricResourceServiceDTO serviceDto = new MetricResourceServiceDTO();
        serviceDto.setServiceName(null);
        serviceDto.setReplicas(List.of(replicaDto));

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(List.of(buildDataPoint()));
        node.setServices(List.of(serviceDto));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }

    @Test
    void shouldThrowWhenServiceNameIsBlank() {
        mockValidAccess();
        Host host = buildHost("machine-1");
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-1"))
                .thenReturn(Optional.of(host));
        when(metricResourceHostRepository.saveAll(any())).thenReturn(List.of());

        MetricResourceServiceReplicaDTO replicaDto = new MetricResourceServiceReplicaDTO();
        replicaDto.setContainerId("container-123");
        replicaDto.setMetrics(List.of(buildDataPoint()));

        MetricResourceServiceDTO serviceDto = new MetricResourceServiceDTO();
        serviceDto.setServiceName("   ");
        serviceDto.setReplicas(List.of(replicaDto));

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(List.of(buildDataPoint()));
        node.setServices(List.of(serviceDto));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }

    @Test
    void shouldThrowWhenNodeInHostsIsNull() {
        mockValidAccess();

        ArrayList<MetricResourceNodeDTO> hosts = new ArrayList<>();
        hosts.add(null);

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(hosts);

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }

    @Test
    void shouldThrowWhenServiceDtoInServicesIsNull() {
        mockValidAccess();
        Host host = buildHost("machine-1");
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-1"))
                .thenReturn(Optional.of(host));
        when(metricResourceHostRepository.saveAll(any())).thenReturn(List.of());

        ArrayList<MetricResourceServiceDTO> services = new ArrayList<>();
        services.add(null);

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(List.of(buildDataPoint()));
        node.setServices(services);

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }

    @Test
    void shouldThrowWhenServiceReplicasIsNull() {
        mockValidAccess();
        Host host = buildHost("machine-1");
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-1"))
                .thenReturn(Optional.of(host));
        when(metricResourceHostRepository.saveAll(any())).thenReturn(List.of());

        MetricResourceServiceDTO serviceDto = new MetricResourceServiceDTO();
        serviceDto.setServiceName("my-service");
        serviceDto.setReplicas(null);

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(List.of(buildDataPoint()));
        node.setServices(List.of(serviceDto));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }

    @Test
    void shouldThrowWhenNodeMetricsContainsNullDataPoint() {
        mockValidAccess();
        Host host = buildHost("machine-1");
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-1"))
                .thenReturn(Optional.of(host));

        ArrayList<MetricDataPointDTO> metrics = new ArrayList<>();
        metrics.add(null);

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(metrics);

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }

    @Test
    void shouldThrowWhenServiceMetricsContainsNullDataPoint() {
        mockValidAccess();
        Host host = buildHost("machine-1");
        when(hostRepository.findByEnvironmentIdAndMachineId(environmentId, "machine-1"))
                .thenReturn(Optional.of(host));
        when(metricResourceHostRepository.saveAll(any())).thenReturn(List.of());

        dk.viplev.api.domain.model.Service svc = new dk.viplev.api.domain.model.Service();
        svc.setId(UUID.randomUUID());
        svc.setServiceName("my-service");
        when(benchmarkServiceScopeService.getActiveScopedServiceIds(benchmarkId)).thenReturn(List.of(svc.getId()));
        when(serviceRepository.findByEnvironmentIdAndServiceNameInAndDeletedAtIsNull(eq(environmentId), anySet()))
                .thenReturn(List.of(svc));

        // Mock replica lookup (returns empty, so a new one will be created)
        when(serviceReplicaRepository.findByServiceIdAndContainerId(eq(svc.getId()), eq("container-123")))
                .thenReturn(Optional.empty());
        
        // Mock replica save
        ServiceReplica savedReplica = new ServiceReplica();
        savedReplica.setId(UUID.randomUUID());
        when(serviceReplicaRepository.save(any())).thenReturn(savedReplica);

        ArrayList<MetricDataPointDTO> metrics = new ArrayList<>();
        metrics.add(null);

        MetricResourceServiceReplicaDTO replicaDto = new MetricResourceServiceReplicaDTO();
        replicaDto.setContainerId("container-123");
        replicaDto.setMetrics(metrics);

        MetricResourceServiceDTO serviceDto = new MetricResourceServiceDTO();
        serviceDto.setServiceName("my-service");
        serviceDto.setReplicas(List.of(replicaDto));

        MetricResourceNodeDTO node = new MetricResourceNodeDTO();
        node.setMachineId("machine-1");
        node.setMetrics(List.of(buildDataPoint()));
        node.setServices(List.of(serviceDto));

        MetricResourceDTO dto = new MetricResourceDTO();
        dto.setHosts(List.of(node));

        assertThatThrownBy(() -> agentService.storeResourceMetrics(environmentId, benchmarkId, runId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid resource metrics");
    }
}
