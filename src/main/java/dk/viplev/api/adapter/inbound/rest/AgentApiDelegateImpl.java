package dk.viplev.api.adapter.inbound.rest;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunStatusUpdateDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MessageDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricPerformanceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MetricResourceDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceRegistrationDTO;
import dk.viplev.api.port.inbound.AgentService;
import dk.viplev.api.port.inbound.ServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentApiDelegateImpl implements AgentApiDelegate {

    private final ServiceService serviceService;
    private final AgentService agentService;

    @Override
    public ResponseEntity<List<MessageDTO>> listMessages(UUID environmentId) {
        log.info("Agent request received: listMessages, environmentId={}", environmentId);
        return ResponseEntity.ok(agentService.listMessages(environmentId));
    }

    @Override
    public ResponseEntity<Void> registerServices(UUID environmentId, ServiceRegistrationDTO serviceRegistrationDTO) {
        log.info("Agent request received: registerServices, environmentId={}", environmentId);

        serviceService.registerServices(environmentId, serviceRegistrationDTO);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<BenchmarkRunDTO> updateBenchmarkRunStatus(UUID environmentId, UUID benchmarkId, UUID runId, BenchmarkRunStatusUpdateDTO benchmarkRunStatusUpdateDTO) {
        log.info("Agent request received: updateBenchmarkRunStatus, environmentId={}, benchmarkId={}, runId={}",
                environmentId, benchmarkId, runId);

        BenchmarkRunDTO dto = agentService.updateBenchmarkRunStatus(environmentId, benchmarkId, runId, benchmarkRunStatusUpdateDTO);

        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Void> storeResourceMetrics(UUID environmentId, UUID benchmarkId, UUID runId, MetricResourceDTO metricResourceDTO) {
        log.info("Agent request received: storeResourceMetrics, environmentId={}, benchmarkId={}, runId={}",
                environmentId, benchmarkId, runId);

        agentService.storeResourceMetrics(environmentId, benchmarkId, runId, metricResourceDTO);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<Void> storePerformanceMetrics(UUID environmentId, UUID benchmarkId, UUID runId, MetricPerformanceDTO metricPerformanceDTO) {
        log.info("Agent request received: storePerformanceMetrics, environmentId={}, benchmarkId={}, runId={}",
                environmentId, benchmarkId, runId);

        agentService.storePerformanceMetrics(environmentId, benchmarkId, runId, metricPerformanceDTO);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
