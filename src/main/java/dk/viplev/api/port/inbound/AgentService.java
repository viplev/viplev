package dk.viplev.api.port.inbound;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunStatusUpdateDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MessageDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricPerformanceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceDTO;

import java.util.List;
import java.util.UUID;

public interface AgentService {

    List<MessageDTO> listMessages(UUID environmentId);

    BenchmarkRunDTO updateBenchmarkRunStatus(UUID environmentId, UUID benchmarkId, UUID runId, BenchmarkRunStatusUpdateDTO dto);

    void storeResourceMetrics(UUID environmentId, UUID benchmarkId, UUID runId, MetricResourceDTO dto);

    void storePerformanceMetrics(UUID environmentId, UUID benchmarkId, UUID runId, MetricPerformanceDTO dto);
}
