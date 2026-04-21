package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ServiceRepository extends JpaRepository<Service, UUID> {

    List<Service> findByEnvironmentId(UUID environmentId);

    List<Service> findByEnvironmentIdAndDeletedAtIsNull(UUID environmentId);

    Optional<Service> findByIdAndEnvironmentId(UUID id, UUID environmentId);

    Optional<Service> findByServiceNameAndEnvironmentId(String serviceName, UUID environmentId);

    List<Service> findByEnvironmentIdAndServiceNameIn(UUID environmentId, Set<String> serviceNames);

    List<Service> findByEnvironmentIdAndServiceNameInAndDeletedAtIsNull(UUID environmentId, Set<String> serviceNames);
}
