package dk.viplev.api.domain.services;

import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.ConflictException;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.BenchmarkService;
import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.domain.model.Service;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.BenchmarkServiceRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceScopeServiceTest {

    @Mock
    private BenchmarkRepository benchmarkRepository;
    @Mock
    private BenchmarkServiceRepository benchmarkServiceRepository;
    @Mock
    private BenchmarkRunRepository benchmarkRunRepository;
    @Mock
    private ServiceRepository serviceRepository;

    private BenchmarkServiceScopeService service;

    @BeforeEach
    void setUp() {
        service = new BenchmarkServiceScopeService(
                benchmarkRepository,
                benchmarkServiceRepository,
                benchmarkRunRepository,
                serviceRepository
        );
    }

    @Test
    void shouldReplaceScopeWithAddRemoveAndReAdd() {
        UUID benchmarkId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();
        UUID serviceC = UUID.randomUUID();

        Benchmark benchmark = benchmark(envId);
        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(benchmarkRunRepository.existsByBenchmarkIdAndStatusIn(any(), any())).thenReturn(false);
        when(serviceRepository.findAllById(List.of(serviceA, serviceC))).thenReturn(List.of(activeService(serviceA, envId), activeService(serviceC, envId)));
        when(benchmarkServiceRepository.findActiveBenchmarkServices(benchmarkId)).thenReturn(List.of(link(serviceA), link(serviceB)));

        service.updateBenchmarkServices(benchmarkId, List.of(serviceA, serviceC));

        verify(benchmarkServiceRepository).softDeleteBenchmarkService(benchmarkId, serviceB);
        verify(benchmarkServiceRepository).insertBenchmarkService(benchmarkId, serviceC);

        when(serviceRepository.findAllById(List.of(serviceA, serviceB))).thenReturn(List.of(activeService(serviceA, envId), activeService(serviceB, envId)));
        when(benchmarkServiceRepository.findActiveBenchmarkServices(benchmarkId)).thenReturn(List.of(link(serviceA), link(serviceC)));

        service.updateBenchmarkServices(benchmarkId, List.of(serviceA, serviceB));

        verify(benchmarkServiceRepository).softDeleteBenchmarkService(benchmarkId, serviceC);
        verify(benchmarkServiceRepository).insertBenchmarkService(benchmarkId, serviceB);
    }

    @Test
    void shouldRejectDuplicateServiceIds() {
        UUID benchmarkId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        assertThatThrownBy(() -> service.updateBenchmarkServices(benchmarkId, List.of(serviceId, serviceId)))
                .isInstanceOf(BadRequestException.class);

        verify(benchmarkRepository, never()).findById(any());
    }

    @Test
    void shouldRejectInvalidOrDeletedOrForeignServices() {
        UUID benchmarkId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        UUID validId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        UUID foreignId = UUID.randomUUID();

        Benchmark benchmark = benchmark(envId);
        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(serviceRepository.findAllById(List.of(validId, deletedId, foreignId))).thenReturn(List.of(
                activeService(validId, envId),
                deletedService(deletedId, envId),
                activeService(foreignId, UUID.randomUUID())
        ));

        assertThatThrownBy(() -> service.updateBenchmarkServices(benchmarkId, List.of(validId, deletedId, foreignId)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldRejectUpdateWhenRunIsActiveOrPending() {
        UUID benchmarkId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        Benchmark benchmark = benchmark(envId);
        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(benchmarkRunRepository.existsByBenchmarkIdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.updateBenchmarkServices(benchmarkId, List.of(serviceId)))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(serviceRepository);
        verify(benchmarkServiceRepository, never()).softDeleteBenchmarkService(any(), any());
        verify(benchmarkServiceRepository, never()).insertBenchmarkService(any(), any());

        ArgumentCaptor<List<BenchmarkRunStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(benchmarkRunRepository).existsByBenchmarkIdAndStatusIn(org.mockito.ArgumentMatchers.eq(benchmarkId), statusesCaptor.capture());
        assertThat(statusesCaptor.getValue())
                .containsExactlyInAnyOrder(BenchmarkRunStatus.PENDING_START, BenchmarkRunStatus.STARTED, BenchmarkRunStatus.PENDING_STOP);
    }

    @Test
    void shouldPrioritizeConflictOverInvalidServicesWhenRunIsActive() {
        UUID benchmarkId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        UUID invalidServiceId = UUID.randomUUID();

        Benchmark benchmark = benchmark(envId);
        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(benchmarkRunRepository.existsByBenchmarkIdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.updateBenchmarkServices(benchmarkId, List.of(invalidServiceId)))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(serviceRepository);
        verify(benchmarkServiceRepository, never()).softDeleteBenchmarkService(any(), any());
        verify(benchmarkServiceRepository, never()).insertBenchmarkService(any(), any());
    }

    private Benchmark benchmark(UUID environmentId) {
        Environment environment = new Environment();
        environment.setId(environmentId);
        Benchmark benchmark = new Benchmark();
        benchmark.setId(UUID.randomUUID());
        benchmark.setEnvironment(environment);
        return benchmark;
    }

    private Service activeService(UUID id, UUID environmentId) {
        Service service = new Service();
        service.setId(id);
        Environment environment = new Environment();
        environment.setId(environmentId);
        service.setEnvironment(environment);
        return service;
    }

    private Service deletedService(UUID id, UUID environmentId) {
        Service service = activeService(id, environmentId);
        service.setDeletedAt(java.time.LocalDateTime.now());
        return service;
    }

    private BenchmarkService link(UUID serviceId) {
        Service service = new Service();
        service.setId(serviceId);
        BenchmarkService link = new BenchmarkService();
        link.setService(service);
        return link;
    }
}
