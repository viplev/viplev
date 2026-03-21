package dk.viplev.api.port.inbound;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunStatusUpdateDTO;

import java.util.UUID;

public interface AgentService {

    BenchmarkRunDTO updateBenchmarkRunStatus(UUID environmentId, UUID benchmarkId, UUID runId, BenchmarkRunStatusUpdateDTO dto);
}
