package dk.viplev.api.adapter.inbound.rest;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunStatusUpdateDTO;
import dk.viplev.api.adapter.inbound.rest.dto.ServiceRegistrationDTO;
import dk.viplev.api.port.inbound.AgentService;
import dk.viplev.api.port.inbound.ServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentApiDelegateImpl implements AgentApiDelegate {

    private final ServiceService serviceService;
    private final AgentService agentService;

    @Override
    public ResponseEntity<Void> registerServices(UUID environmentId, ServiceRegistrationDTO serviceRegistrationDTO) {
        serviceService.registerServices(environmentId, serviceRegistrationDTO);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<BenchmarkRunDTO> updateBenchmarkRunStatus(UUID environmentId, UUID benchmarkId, UUID runId, BenchmarkRunStatusUpdateDTO benchmarkRunStatusUpdateDTO) {
        BenchmarkRunDTO dto = agentService.updateBenchmarkRunStatus(environmentId, benchmarkId, runId, benchmarkRunStatusUpdateDTO);
        return ResponseEntity.ok(dto);
    }
}
