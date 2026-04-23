package dk.viplev.api.domain.services;

import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.ConflictException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.BenchmarkService;
import dk.viplev.api.domain.model.Service;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.BenchmarkServiceRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BenchmarkServiceScopeService {

    private static final List<BenchmarkRunStatus> ACTIVE_OR_PENDING_RUN_STATUSES = List.of(
            BenchmarkRunStatus.PENDING_START,
            BenchmarkRunStatus.STARTED,
            BenchmarkRunStatus.PENDING_STOP
    );

    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkServiceRepository benchmarkServiceRepository;
    private final BenchmarkRunRepository benchmarkRunRepository;
    private final ServiceRepository serviceRepository;

    @Transactional
    public void updateBenchmarkServices(UUID benchmarkId, List<UUID> serviceIds) {
        if (serviceIds == null) {
            throw new BadRequestException("Invalid service scope", "serviceIds must not be null");
        }

        validateNoDuplicates(serviceIds);

        Benchmark benchmark = benchmarkRepository.findById(benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark not found"));

        if (hasActiveOrPendingRun(benchmarkId)) {
            throw new ConflictException("Benchmark has active or pending run",
                    "Cannot update scoped services while benchmark run is pending or active");
        }

        validateAllServicesExistAndAreActiveInEnvironment(benchmark, serviceIds);

        Set<UUID> requested = new HashSet<>(serviceIds);
        Set<UUID> current = new HashSet<>(getActiveScopedServiceIds(benchmarkId));

        Set<UUID> toRemove = new HashSet<>(current);
        toRemove.removeAll(requested);

        Set<UUID> toAdd = new HashSet<>(requested);
        toAdd.removeAll(current);

        for (UUID serviceId : toRemove) {
            benchmarkServiceRepository.softDeleteBenchmarkService(benchmarkId, serviceId);
        }

        for (UUID serviceId : toAdd) {
            benchmarkServiceRepository.insertBenchmarkService(benchmarkId, serviceId);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> getActiveScopedServiceIds(UUID benchmarkId) {
        return benchmarkServiceRepository.findActiveBenchmarkServices(benchmarkId).stream()
                .map(BenchmarkService::getService)
                .map(Service::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveOrPendingRun(UUID benchmarkId) {
        return benchmarkRunRepository.existsByBenchmarkIdAndStatusIn(benchmarkId, ACTIVE_OR_PENDING_RUN_STATUSES);
    }

    private void validateNoDuplicates(List<UUID> serviceIds) {
        Set<UUID> unique = new HashSet<>(serviceIds);
        if (unique.size() != serviceIds.size()) {
            throw new BadRequestException("Duplicate service ids", "serviceIds must be unique");
        }
    }

    private void validateAllServicesExistAndAreActiveInEnvironment(Benchmark benchmark, List<UUID> serviceIds) {
        if (serviceIds.isEmpty()) {
            return;
        }

        UUID environmentId = benchmark.getEnvironment().getId();
        List<Service> activeServices = serviceRepository.findAllById(serviceIds).stream()
                .filter(service -> service.getDeletedAt() == null)
                .filter(service -> service.getEnvironment().getId().equals(environmentId))
                .toList();

        Set<UUID> activeServiceIds = activeServices.stream()
                .map(Service::getId)
                .collect(Collectors.toSet());

        List<UUID> invalid = serviceIds.stream()
                .filter(id -> !activeServiceIds.contains(id))
                .toList();

        if (!invalid.isEmpty()) {
            throw new BadRequestException("Invalid services",
                    "Some services do not exist, are deleted, or belong to a different environment: " + invalid);
        }
    }
}
