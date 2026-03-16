package dk.viplev.api.port.inbound;

import dk.viplev.api.adapter.inbound.rest.dto.EnvironmentDTO;

import java.util.List;
import java.util.UUID;

public interface EnvironmentService {

    List<EnvironmentDTO> listEnvironments();

    EnvironmentDTO createEnvironment(EnvironmentDTO request);

    EnvironmentDTO getEnvironment(UUID environmentId);

    EnvironmentDTO updateEnvironment(UUID environmentId, EnvironmentDTO request);

    void deleteEnvironment(UUID environmentId);
}
