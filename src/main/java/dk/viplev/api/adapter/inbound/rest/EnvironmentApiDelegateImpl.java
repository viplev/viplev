package dk.viplev.api.adapter.inbound.rest;

import dk.viplev.api.adapter.inbound.rest.dto.EnvironmentDTO;
import dk.viplev.api.port.inbound.EnvironmentService;
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
public class EnvironmentApiDelegateImpl implements EnvironmentApiDelegate {

    private final EnvironmentService environmentService;

    @Override
    public ResponseEntity<List<EnvironmentDTO>> listEnvironments() {
        return ResponseEntity.ok(environmentService.listEnvironments());
    }

    @Override
    public ResponseEntity<EnvironmentDTO> createEnvironment(EnvironmentDTO environmentDTO) {
        EnvironmentDTO created = environmentService.createEnvironment(environmentDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<EnvironmentDTO> getEnvironment(UUID environmentId) {
        return ResponseEntity.ok(environmentService.getEnvironment(environmentId));
    }

    @Override
    public ResponseEntity<EnvironmentDTO> updateEnvironment(UUID environmentId, EnvironmentDTO environmentDTO) {
        return ResponseEntity.ok(environmentService.updateEnvironment(environmentId, environmentDTO));
    }

    @Override
    public ResponseEntity<Void> deleteEnvironment(UUID environmentId) {
        environmentService.deleteEnvironment(environmentId);
        return ResponseEntity.noContent().build();
    }
}
