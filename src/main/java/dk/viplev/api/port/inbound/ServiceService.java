package dk.viplev.api.port.inbound;

import dk.viplev.api.adapter.inbound.rest.dto.ServiceDTO;

import java.util.List;
import java.util.UUID;

public interface ServiceService {

    List<ServiceDTO> listServices(UUID environmentId);

    ServiceDTO getService(UUID environmentId, UUID serviceId);

    void registerServices(UUID environmentId, List<ServiceDTO> services);
}
