package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.ServiceReplica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ServiceReplicaRepository extends JpaRepository<ServiceReplica, UUID> {

    List<ServiceReplica> findByServiceIdAndDeletedAtIsNull(UUID serviceId);

    Optional<ServiceReplica> findByServiceIdAndContainerId(UUID serviceId, String containerId);

    Optional<ServiceReplica> findByServiceIdAndContainerIdAndDeletedAtIsNull(UUID serviceId, String containerId);

    List<ServiceReplica> findByServiceIdAndContainerIdIn(UUID serviceId, Set<String> containerIds);

    List<ServiceReplica> findByHostIdAndDeletedAtIsNull(UUID hostId);
}
