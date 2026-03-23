package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository extends JpaRepository<Service, UUID> {

    List<Service> findByHostEnvironmentId(UUID environmentId);

    List<Service> findByHostId(UUID hostId);

    Optional<Service> findByIdAndHostEnvironmentId(UUID id, UUID environmentId);

    Optional<Service> findByServiceNameAndHostId(String serviceName, UUID hostId);
}
