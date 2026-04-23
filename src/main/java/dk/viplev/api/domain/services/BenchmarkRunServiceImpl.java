package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDerivedDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunRawDTO;
import dk.viplev.api.adapter.inbound.rest.dto.DerivedHostSummaryDTO;
import dk.viplev.api.adapter.inbound.rest.dto.DerivedHttpSummaryDTO;
import dk.viplev.api.adapter.inbound.rest.dto.DerivedHttpTimingDTO;
import dk.viplev.api.adapter.inbound.rest.dto.DerivedResourceStatsDTO;
import dk.viplev.api.adapter.inbound.rest.dto.DerivedResourceSummaryDTO;
import dk.viplev.api.adapter.inbound.rest.dto.DerivedServiceSummaryDTO;
import dk.viplev.api.adapter.inbound.rest.dto.DerivedVusSummaryDTO;
import dk.viplev.api.adapter.inbound.rest.dto.EnvironmentRunsDTO;
import dk.viplev.api.adapter.inbound.rest.dto.PaginationDTO;
import dk.viplev.api.adapter.inbound.rest.dto.RawHostTimeSeriesDTO;
import dk.viplev.api.adapter.inbound.rest.dto.RawK6DataPointDTO;
import dk.viplev.api.adapter.inbound.rest.dto.RawK6TimeSeriesDTO;
import dk.viplev.api.adapter.inbound.rest.dto.RawReplicaTimeSeriesDTO;
import dk.viplev.api.adapter.inbound.rest.dto.RawResourceDataPointDTO;
import dk.viplev.api.adapter.inbound.rest.dto.RawServiceTimeSeriesDTO;
import dk.viplev.api.adapter.inbound.rest.dto.RawTimeSeriesDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.BenchmarkRunMapper;
import dk.viplev.api.domain.exception.BadRequestException;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.domain.model.MetricK6Http;
import dk.viplev.api.domain.model.MetricK6Vus;
import dk.viplev.api.domain.model.MetricResourceHost;
import dk.viplev.api.domain.model.MetricResourceReplica;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.inbound.BenchmarkRunService;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;
import dk.viplev.api.port.outbound.db.MetricK6HttpRepository;
import dk.viplev.api.port.outbound.db.MetricK6VusRepository;
import dk.viplev.api.port.outbound.db.MetricResourceHostRepository;
import dk.viplev.api.port.outbound.db.MetricResourceReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BenchmarkRunServiceImpl implements BenchmarkRunService {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final EnvironmentRepository environmentRepository;
    private final AuthService authService;
    private final BenchmarkRunMapper benchmarkRunMapper;
    private final MetricK6HttpRepository metricK6HttpRepository;
    private final MetricK6VusRepository metricK6VusRepository;
    private final MetricResourceHostRepository metricResourceHostRepository;
    private final MetricResourceReplicaRepository metricResourceReplicaRepository;

    @Override
    public List<BenchmarkRunDTO> listBenchmarkRuns(UUID environmentId, UUID benchmarkId) {
        findEnvironmentByOwner(environmentId);
        findBenchmarkByEnvironment(benchmarkId, environmentId);
        return benchmarkRunRepository.findByBenchmarkId(benchmarkId).stream()
                .map(benchmarkRunMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EnvironmentRunsDTO listEnvironmentRuns(UUID environmentId, Integer page, Integer size, String sort) {
        findEnvironmentByOwner(environmentId);

        int pageNumber = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        if (pageNumber < 0) {
            throw new BadRequestException("Page must be >= 0, got: " + pageNumber);
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BadRequestException("Size must be between 1 and 100, got: " + pageSize);
        }

        Sort sorting = parseSort(sort != null ? sort : "status,asc;startedAt,desc");

        Page<BenchmarkRun> resultPage = benchmarkRunRepository.findByEnvironmentId(
                environmentId, PageRequest.of(pageNumber, pageSize, sorting));

        EnvironmentRunsDTO dto = new EnvironmentRunsDTO();
        dto.setRuns(resultPage.getContent().stream()
                .map(benchmarkRunMapper::toSummaryDto)
                .toList());

        PaginationDTO pagination = new PaginationDTO();
        pagination.setPage(pageNumber);
        pagination.setSize(pageSize);
        long totalElementsLong = resultPage.getTotalElements();
        int totalElements = totalElementsLong > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) totalElementsLong;
        pagination.setTotalElements(totalElements);
        pagination.setTotalPages(resultPage.getTotalPages());
        dto.setPagination(pagination);

        return dto;
    }

    private static final Set<String> SORTABLE_FIELDS = Set.of("status", "startedAt", "finishedAt");

    private Sort parseSort(String sort) {
        List<Sort.Order> orders = new ArrayList<>();
        for (String part : sort.split(";")) {
            String[] tokens = part.trim().split(",");
            if (tokens.length != 2) {
                throw new BadRequestException("Invalid sort format: " + part + ". Expected format: field,direction");
            }
            String field = tokens[0].trim();
            String direction = tokens[1].trim().toLowerCase();
            if (!SORTABLE_FIELDS.contains(field)) {
                throw new BadRequestException("Invalid sort field: " + field + ". Allowed: " + SORTABLE_FIELDS);
            }
            if (!"asc".equals(direction) && !"desc".equals(direction)) {
                throw new BadRequestException("Invalid sort direction: " + direction + ". Allowed: asc, desc");
            }
            orders.add(new Sort.Order(
                    "desc".equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC,
                    field));
        }
        return Sort.by(orders);
    }

    @Override
    @Transactional(readOnly = true)
    public BenchmarkRunDerivedDTO getBenchmarkRunDerived(UUID environmentId, UUID benchmarkId, UUID runId, String percentiles) {
        findEnvironmentByOwner(environmentId);
        findBenchmarkByEnvironment(benchmarkId, environmentId);
        BenchmarkRun run = benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark run not found"));

        List<Double> percentileValues = parsePercentiles(percentiles);

        List<MetricK6Http> httpMetrics = metricK6HttpRepository.findByBenchmarkRunId(runId);
        List<MetricK6Vus> vusMetrics = metricK6VusRepository.findByBenchmarkRunId(runId);
        List<MetricResourceHost> hostMetrics = metricResourceHostRepository.findByBenchmarkRunId(runId);
        List<MetricResourceReplica> replicaMetrics = metricResourceReplicaRepository.findByBenchmarkRunId(runId);

        BenchmarkRunDerivedDTO result = new BenchmarkRunDerivedDTO();
        result.setRun(benchmarkRunMapper.toDto(run));
        result.setHttp(buildHttpSummaries(httpMetrics, percentileValues, percentiles));
        result.setVus(buildVusSummary(vusMetrics));
        result.setHosts(buildHostSummaries(hostMetrics, replicaMetrics, percentileValues, percentiles));

        return result;
    }

    @Override
    @Transactional
    public void deleteBenchmarkRun(UUID environmentId, UUID benchmarkId, UUID runId) {
        findEnvironmentByOwner(environmentId);
        findBenchmarkByEnvironment(benchmarkId, environmentId);
        BenchmarkRun run = benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark run not found"));
        benchmarkRunRepository.delete(run);
    }

    @Override
    @Transactional(readOnly = true)
    public BenchmarkRunRawDTO getBenchmarkRunData(UUID environmentId, UUID benchmarkId, UUID runId) {
        findEnvironmentByOwner(environmentId);
        findBenchmarkByEnvironment(benchmarkId, environmentId);
        benchmarkRunRepository.findByIdAndBenchmarkId(runId, benchmarkId)
                .orElseThrow(() -> new NotFoundException("Benchmark run not found"));

        List<MetricResourceHost> hostMetrics = metricResourceHostRepository.findByBenchmarkRunId(runId);
        List<MetricResourceReplica> replicaMetrics = metricResourceReplicaRepository.findByBenchmarkRunId(runId);
        List<MetricK6Http> httpMetrics = metricK6HttpRepository.findByBenchmarkRunId(runId);
        List<MetricK6Vus> vusMetrics = metricK6VusRepository.findByBenchmarkRunId(runId);

        RawTimeSeriesDTO timeSeries = new RawTimeSeriesDTO();
        timeSeries.setHosts(buildRawHosts(hostMetrics, replicaMetrics));
        timeSeries.setK6(buildRawK6(httpMetrics, vusMetrics));

        BenchmarkRunRawDTO result = new BenchmarkRunRawDTO();
        result.setTimeSeries(timeSeries);
        return result;
    }

    private List<RawHostTimeSeriesDTO> buildRawHosts(List<MetricResourceHost> hostMetrics,
                                                      List<MetricResourceReplica> replicaMetrics) {
        if (hostMetrics.isEmpty() && replicaMetrics.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<MetricResourceHost>> groupedByHost = hostMetrics.stream()
                .collect(Collectors.groupingBy(m -> m.getHost().getId(), LinkedHashMap::new, Collectors.toList()));

        // Group replica metrics by host (via replica -> host)
        Map<UUID, List<MetricResourceReplica>> replicasByHost = replicaMetrics.stream()
                .collect(Collectors.groupingBy(m -> m.getReplica().getHost().getId(), LinkedHashMap::new, Collectors.toList()));

        // Union of all host IDs from host metrics and replica metrics
        Map<UUID, String> allHosts = new LinkedHashMap<>();
        hostMetrics.forEach(m -> allHosts.put(m.getHost().getId(), m.getHost().getName()));
        replicaMetrics.forEach(m -> allHosts.put(m.getReplica().getHost().getId(), m.getReplica().getHost().getName()));

        return allHosts.entrySet().stream().map(entry -> {
            UUID hostId = entry.getKey();
            String hostName = entry.getValue();

            RawHostTimeSeriesDTO hostDto = new RawHostTimeSeriesDTO();
            hostDto.setHostId(hostId);
            hostDto.setHostName(hostName);

            List<MetricResourceHost> metrics = groupedByHost.getOrDefault(hostId, List.of());
            hostDto.setDataPoints(metrics.stream()
                    .sorted(Comparator.comparing(MetricResourceHost::getCollectedAt))
                    .map(this::toRawResourceDataPoint)
                    .toList());

            List<MetricResourceReplica> hostReplicas = replicasByHost.getOrDefault(hostId, List.of());
            
            if (!hostReplicas.isEmpty()) {
                // Group replica metrics by service
                Map<UUID, List<MetricResourceReplica>> replicasByService = hostReplicas.stream()
                        .collect(Collectors.groupingBy(m -> m.getReplica().getService().getId(), LinkedHashMap::new, Collectors.toList()));

                // Get service IDs and names from replicas
                Map<UUID, String> allServices = new LinkedHashMap<>();
                hostReplicas.forEach(m -> allServices.put(m.getReplica().getService().getId(), m.getReplica().getService().getServiceName()));

                hostDto.setServices(allServices.entrySet().stream().map(sEntry -> {
                    UUID serviceId = sEntry.getKey();
                    String serviceName = sEntry.getValue();
                    
                    RawServiceTimeSeriesDTO serviceDto = new RawServiceTimeSeriesDTO();
                    serviceDto.setServiceId(serviceId);
                    serviceDto.setServiceName(serviceName);

                    // Group replica metrics by replica ID to create separate time series per replica
                    List<MetricResourceReplica> serviceReplicas = replicasByService.getOrDefault(serviceId, List.of());
                    Map<UUID, List<MetricResourceReplica>> metricsByReplica = serviceReplicas.stream()
                            .collect(Collectors.groupingBy(m -> m.getReplica().getId(), LinkedHashMap::new, Collectors.toList()));

                    // Build replica-level time series
                    serviceDto.setReplicas(metricsByReplica.entrySet().stream().map(rEntry -> {
                        UUID replicaId = rEntry.getKey();
                        List<MetricResourceReplica> replicaMetricsList = rEntry.getValue();
                        
                        // Get container ID from first metric (all metrics for same replica have same container ID)
                        String containerId = replicaMetricsList.isEmpty() ? null : replicaMetricsList.get(0).getReplica().getContainerId();
                        
                        RawReplicaTimeSeriesDTO replicaDto = new RawReplicaTimeSeriesDTO();
                        replicaDto.setReplicaId(replicaId);
                        replicaDto.setContainerId(containerId);
                        replicaDto.setDataPoints(replicaMetricsList.stream()
                                .sorted(Comparator.comparing(MetricResourceReplica::getCollectedAt))
                                .map(this::toRawResourceDataPoint)
                                .toList());
                        
                        return replicaDto;
                    }).sorted(Comparator.comparing(RawReplicaTimeSeriesDTO::getContainerId)).toList());
                    
                    return serviceDto;
                }).sorted(Comparator.comparing(RawServiceTimeSeriesDTO::getServiceName)).toList());
            } else {
                hostDto.setServices(List.of());
            }

            return hostDto;
        }).sorted(Comparator.comparing(RawHostTimeSeriesDTO::getHostName)).toList();
    }

    private RawResourceDataPointDTO toRawResourceDataPoint(MetricResourceHost m) {
        RawResourceDataPointDTO dp = new RawResourceDataPointDTO();
        dp.setTimestamp(m.getCollectedAt());
        dp.setCpuPercentage(m.getCpuPercentage());
        dp.setMemoryUsageBytes(m.getMemoryUsageBytes());
        dp.setMemoryLimitBytes(m.getMemoryLimitBytes());
        dp.setNetworkInBytes(m.getNetworkInBytes());
        dp.setNetworkOutBytes(m.getNetworkOutBytes());
        dp.setBlockInBytes(m.getBlockInBytes());
        dp.setBlockOutBytes(m.getBlockOutBytes());
        return dp;
    }

    private RawResourceDataPointDTO toRawResourceDataPoint(MetricResourceReplica m) {
        RawResourceDataPointDTO dp = new RawResourceDataPointDTO();
        dp.setTimestamp(m.getCollectedAt());
        dp.setCpuPercentage(m.getCpuPercentage());
        dp.setMemoryUsageBytes(m.getMemoryUsageBytes());
        dp.setMemoryLimitBytes(m.getMemoryLimitBytes());
        dp.setNetworkInBytes(m.getNetworkInBytes());
        dp.setNetworkOutBytes(m.getNetworkOutBytes());
        dp.setBlockInBytes(m.getBlockInBytes());
        dp.setBlockOutBytes(m.getBlockOutBytes());
        return dp;
    }

    private RawK6TimeSeriesDTO buildRawK6(List<MetricK6Http> httpMetrics, List<MetricK6Vus> vusMetrics) {
        if (httpMetrics.isEmpty() && vusMetrics.isEmpty()) {
            return null;
        }

        List<RawK6DataPointDTO> dataPoints = Stream.concat(
                httpMetrics.stream().map(m -> {
                    RawK6DataPointDTO dp = new RawK6DataPointDTO();
                    dp.setTimestamp(m.getCollectedAt());
                    dp.setHttpResponseTimeMs(m.getHttpReqDurationMs());
                    dp.setHttpWaitingMs(m.getHttpReqWaitingMs());
                    return dp;
                }),
                vusMetrics.stream().map(m -> {
                    RawK6DataPointDTO dp = new RawK6DataPointDTO();
                    dp.setTimestamp(m.getCollectedAt());
                    dp.setVus(m.getVus());
                    return dp;
                })
        ).sorted(Comparator.comparing(RawK6DataPointDTO::getTimestamp)).toList();

        RawK6TimeSeriesDTO k6 = new RawK6TimeSeriesDTO();
        k6.setDataPoints(dataPoints);
        return k6;
    }

    private List<DerivedHttpSummaryDTO> buildHttpSummaries(List<MetricK6Http> metrics, List<Double> percentileValues, String percentileNames) {
        if (metrics.isEmpty()) {
            return List.of();
        }

        Map<String, List<MetricK6Http>> grouped = metrics.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getRequestGroup() != null ? m.getRequestGroup() : "",
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<String> pNames = parsePercentileNames(percentileNames);

        return grouped.entrySet().stream().map(entry -> {
            String group = entry.getKey();
            List<MetricK6Http> groupMetrics = entry.getValue();

            int totalRequests = groupMetrics.size();
            long errorCount = groupMetrics.stream()
                    .filter(m -> m.getHttpStatus() != null && m.getExpectedStatus() != null
                            && !m.getHttpStatus().equals(m.getExpectedStatus()))
                    .count();
            double errorRate = totalRequests > 0 ? (double) errorCount / totalRequests * 100.0 : 0.0;

            long totalDataReceived = groupMetrics.stream()
                    .filter(m -> m.getDataReceivedByte() != null)
                    .mapToLong(MetricK6Http::getDataReceivedByte)
                    .sum();
            long totalDataSent = groupMetrics.stream()
                    .filter(m -> m.getDataSentByte() != null)
                    .mapToLong(MetricK6Http::getDataSentByte)
                    .sum();

            LocalDateTime minTime = groupMetrics.stream()
                    .map(MetricK6Http::getCollectedAt)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            LocalDateTime maxTime = groupMetrics.stream()
                    .map(MetricK6Http::getCollectedAt)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            double durationSeconds = 1.0;
            if (minTime != null && maxTime != null && !minTime.equals(maxTime)) {
                durationSeconds = Duration.between(minTime, maxTime).toMillis() / 1000.0;
            }
            double requestsPerSecond = totalRequests / durationSeconds;

            List<Double> durationValues = groupMetrics.stream()
                    .filter(m -> m.getHttpReqDurationMs() != null)
                    .map(m -> m.getHttpReqDurationMs().doubleValue())
                    .collect(Collectors.toList());

            List<Double> waitingValues = groupMetrics.stream()
                    .filter(m -> m.getHttpReqWaitingMs() != null)
                    .map(m -> m.getHttpReqWaitingMs().doubleValue())
                    .collect(Collectors.toList());

            String url = groupMetrics.stream()
                    .map(MetricK6Http::getUrl)
                    .filter(u -> u != null)
                    .findFirst()
                    .orElse(null);

            DerivedHttpSummaryDTO summary = new DerivedHttpSummaryDTO();
            summary.setRequestGroup(group.isEmpty() ? null : group);
            summary.setUrl(url);
            summary.setTotalRequests(totalRequests);
            summary.setRequestsPerSecond(requestsPerSecond);
            summary.setErrorRate(errorRate);
            summary.setTotalDataReceivedBytes(totalDataReceived);
            summary.setTotalDataSentBytes(totalDataSent);
            summary.setDuration(buildHttpTiming(durationValues, percentileValues, pNames));
            summary.setWaiting(buildHttpTiming(waitingValues, percentileValues, pNames));

            return summary;
        }).sorted(Comparator.comparing(s -> s.getRequestGroup() != null ? s.getRequestGroup() : "")).toList();
    }

    private DerivedHttpTimingDTO buildHttpTiming(List<Double> values, List<Double> percentileValues, List<String> percentileNames) {
        if (values.isEmpty()) {
            return null;
        }

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        double avg = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = sorted.getFirst();
        double max = sorted.getLast();
        double median = computePercentile(sorted, 50.0);

        Map<String, Double> percentiles = new LinkedHashMap<>();
        for (int i = 0; i < percentileValues.size(); i++) {
            percentiles.put(percentileNames.get(i), computePercentile(sorted, percentileValues.get(i)));
        }

        DerivedHttpTimingDTO timing = new DerivedHttpTimingDTO();
        timing.setAvg(avg);
        timing.setMin(min);
        timing.setMax(max);
        timing.setMedian(median);
        timing.setPercentiles(percentiles);

        return timing;
    }

    private DerivedVusSummaryDTO buildVusSummary(List<MetricK6Vus> metrics) {
        if (metrics.isEmpty()) {
            return null;
        }

        List<Integer> values = metrics.stream()
                .map(MetricK6Vus::getVus)
                .filter(v -> v != null)
                .toList();

        if (values.isEmpty()) {
            return null;
        }

        int min = values.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = values.stream().mapToInt(Integer::intValue).max().orElse(0);
        double avg = values.stream().mapToInt(Integer::intValue).average().orElse(0.0);

        DerivedVusSummaryDTO vus = new DerivedVusSummaryDTO();
        vus.setMin(min);
        vus.setMax(max);
        vus.setAvg(avg);

        return vus;
    }

    private List<DerivedHostSummaryDTO> buildHostSummaries(List<MetricResourceHost> hostMetrics,
                                                           List<MetricResourceReplica> replicaMetrics,
                                                           List<Double> percentileValues,
                                                           String percentileNames) {
        if (hostMetrics.isEmpty() && replicaMetrics.isEmpty()) {
            return List.of();
        }

        List<String> pNames = parsePercentileNames(percentileNames);

        Map<UUID, List<MetricResourceHost>> groupedByHost = hostMetrics.stream()
                .collect(Collectors.groupingBy(m -> m.getHost().getId(), LinkedHashMap::new, Collectors.toList()));

        Map<UUID, List<MetricResourceReplica>> replicasByHost = replicaMetrics.stream()
                .collect(Collectors.groupingBy(m -> m.getReplica().getHost().getId(), LinkedHashMap::new, Collectors.toList()));

        // Union of all host IDs from host metrics and replica metrics
        Map<UUID, String> allHosts = new LinkedHashMap<>();
        hostMetrics.forEach(m -> allHosts.put(m.getHost().getId(), m.getHost().getName()));
        replicaMetrics.forEach(m -> allHosts.put(m.getReplica().getHost().getId(), m.getReplica().getHost().getName()));

        return allHosts.entrySet().stream().map(entry -> {
            UUID hostId = entry.getKey();
            String hostName = entry.getValue();

            DerivedHostSummaryDTO hostSummary = new DerivedHostSummaryDTO();
            hostSummary.setHostId(hostId);
            hostSummary.setHostName(hostName);

            List<MetricResourceHost> metrics = groupedByHost.getOrDefault(hostId, List.of());
            hostSummary.setResource(metrics.isEmpty() ? null : buildResourceSummaryFromHost(metrics, percentileValues, pNames));

            List<MetricResourceReplica> hostReplicas = replicasByHost.getOrDefault(hostId, List.of());
            
            if (!hostReplicas.isEmpty()) {
                // Group replica metrics by service
                Map<UUID, List<MetricResourceReplica>> replicasByService = hostReplicas.stream()
                        .collect(Collectors.groupingBy(m -> m.getReplica().getService().getId(), LinkedHashMap::new, Collectors.toList()));

                // Get service IDs and names from replicas
                Map<UUID, String> allServices = new LinkedHashMap<>();
                hostReplicas.forEach(m -> allServices.put(m.getReplica().getService().getId(), m.getReplica().getService().getServiceName()));

                List<DerivedServiceSummaryDTO> serviceSummaries = allServices.entrySet().stream().map(sEntry -> {
                    UUID serviceId = sEntry.getKey();
                    String serviceName = sEntry.getValue();

                    DerivedServiceSummaryDTO serviceSummary = new DerivedServiceSummaryDTO();
                    serviceSummary.setServiceId(serviceId);
                    serviceSummary.setServiceName(serviceName);

                    // Use replica-level metrics for aggregation
                    List<MetricResourceReplica> repMetrics = replicasByService.getOrDefault(serviceId, List.of());
                    serviceSummary.setResource(buildResourceSummaryFromReplicas(repMetrics, percentileValues, pNames));

                    return serviceSummary;
                }).sorted(Comparator.comparing(DerivedServiceSummaryDTO::getServiceName)).toList();

                hostSummary.setServices(serviceSummaries);
            } else {
                hostSummary.setServices(List.of());
            }

            return hostSummary;
        }).sorted(Comparator.comparing(DerivedHostSummaryDTO::getHostName)).toList();
    }

    private DerivedResourceSummaryDTO buildResourceSummaryFromHost(List<MetricResourceHost> metrics,
                                                                    List<Double> percentileValues,
                                                                    List<String> percentileNames) {
        List<Double> cpuValues = metrics.stream()
                .filter(m -> m.getCpuPercentage() != null)
                .map(MetricResourceHost::getCpuPercentage)
                .collect(Collectors.toList());

        List<Double> memoryValues = metrics.stream()
                .filter(m -> m.getMemoryUsageBytes() != null)
                .map(m -> m.getMemoryUsageBytes().doubleValue())
                .collect(Collectors.toList());

        long networkIn = metrics.stream()
                .filter(m -> m.getNetworkInBytes() != null)
                .mapToLong(MetricResourceHost::getNetworkInBytes)
                .sum();
        long networkOut = metrics.stream()
                .filter(m -> m.getNetworkOutBytes() != null)
                .mapToLong(MetricResourceHost::getNetworkOutBytes)
                .sum();
        long blockIn = metrics.stream()
                .filter(m -> m.getBlockInBytes() != null)
                .mapToLong(MetricResourceHost::getBlockInBytes)
                .sum();
        long blockOut = metrics.stream()
                .filter(m -> m.getBlockOutBytes() != null)
                .mapToLong(MetricResourceHost::getBlockOutBytes)
                .sum();

        DerivedResourceSummaryDTO summary = new DerivedResourceSummaryDTO();
        summary.setCpu(buildResourceStats(cpuValues, percentileValues, percentileNames));
        summary.setMemory(buildResourceStats(memoryValues, percentileValues, percentileNames));
        summary.setNetworkInTotalBytes(networkIn);
        summary.setNetworkOutTotalBytes(networkOut);
        summary.setBlockInTotalBytes(blockIn);
        summary.setBlockOutTotalBytes(blockOut);

        return summary;
    }

    private DerivedResourceSummaryDTO buildResourceSummaryFromReplicas(List<MetricResourceReplica> replicaMetrics,
                                                                        List<Double> percentileValues,
                                                                        List<String> percentileNames) {
        List<Double> cpuValues = replicaMetrics.stream()
                .filter(m -> m.getCpuPercentage() != null)
                .map(MetricResourceReplica::getCpuPercentage)
                .toList();

        List<Double> memoryValues = replicaMetrics.stream()
                .filter(m -> m.getMemoryUsageBytes() != null)
                .map(m -> m.getMemoryUsageBytes().doubleValue())
                .toList();

        long networkIn = replicaMetrics.stream()
                .filter(m -> m.getNetworkInBytes() != null)
                .mapToLong(MetricResourceReplica::getNetworkInBytes)
                .sum();

        long networkOut = replicaMetrics.stream()
                .filter(m -> m.getNetworkOutBytes() != null)
                .mapToLong(MetricResourceReplica::getNetworkOutBytes)
                .sum();

        long blockIn = replicaMetrics.stream()
                .filter(m -> m.getBlockInBytes() != null)
                .mapToLong(MetricResourceReplica::getBlockInBytes)
                .sum();

        long blockOut = replicaMetrics.stream()
                .filter(m -> m.getBlockOutBytes() != null)
                .mapToLong(MetricResourceReplica::getBlockOutBytes)
                .sum();

        DerivedResourceSummaryDTO summary = new DerivedResourceSummaryDTO();
        summary.setCpu(buildResourceStats(cpuValues, percentileValues, percentileNames));
        summary.setMemory(buildResourceStats(memoryValues, percentileValues, percentileNames));
        summary.setNetworkInTotalBytes(networkIn);
        summary.setNetworkOutTotalBytes(networkOut);
        summary.setBlockInTotalBytes(blockIn);
        summary.setBlockOutTotalBytes(blockOut);

        return summary;
    }

    private DerivedResourceStatsDTO buildResourceStats(List<Double> values, List<Double> percentileValues, List<String> percentileNames) {
        if (values.isEmpty()) {
            return null;
        }

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        double avg = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = sorted.getLast();

        Map<String, Double> percentiles = new LinkedHashMap<>();
        for (int i = 0; i < percentileValues.size(); i++) {
            percentiles.put(percentileNames.get(i), computePercentile(sorted, percentileValues.get(i)));
        }

        DerivedResourceStatsDTO stats = new DerivedResourceStatsDTO();
        stats.setAvg(avg);
        stats.setMax(max);
        stats.setPercentiles(percentiles);

        return stats;
    }

    private List<Double> parsePercentiles(String percentiles) {
        if (percentiles == null || percentiles.isBlank()) {
            return List.of(90.0, 95.0);
        }

        List<Double> result = new ArrayList<>();
        for (String p : percentiles.split(",")) {
            String trimmed = p.trim().toLowerCase();
            if (!trimmed.startsWith("p")) {
                throw new BadRequestException("Invalid percentile format: " + p + ". Expected format: p90, p95, p99, p999");
            }
            try {
                String numPart = trimmed.substring(1);
                if (numPart.contains(".")) {
                    throw new BadRequestException("Invalid percentile format: " + p + ". Use p999 instead of p99.9");
                }
                double value;
                if (numPart.length() > 2) {
                    value = Double.parseDouble(numPart.substring(0, 2) + "." + numPart.substring(2));
                } else {
                    value = Double.parseDouble(numPart);
                }
                if (value <= 0 || value >= 100) {
                    throw new BadRequestException("Percentile must be between 0 and 100: " + p);
                }
                result.add(value);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid percentile format: " + p);
            }
        }
        return result;
    }

    private List<String> parsePercentileNames(String percentiles) {
        if (percentiles == null || percentiles.isBlank()) {
            return List.of("p90", "p95");
        }
        List<String> names = new ArrayList<>();
        for (String p : percentiles.split(",")) {
            names.add(p.trim().toLowerCase());
        }
        return names;
    }

    private double computePercentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.getFirst();
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    private Environment findEnvironmentByOwner(UUID environmentId) {
        UUID ownerId = authService.getAuthenticatedUserId();
        return environmentRepository.findByIdAndOwnerId(environmentId, ownerId)
                .orElseThrow(() -> new NotFoundException("Environment not found"));
    }

    private Benchmark findBenchmarkByEnvironment(UUID benchmarkId, UUID environmentId) {
        return benchmarkRepository.findByIdAndEnvironmentId(benchmarkId, environmentId)
                .orElseThrow(() -> new NotFoundException("Benchmark not found"));
    }
}
