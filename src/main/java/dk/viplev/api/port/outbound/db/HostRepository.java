package dk.viplev.api.port.outbound.db;

import dk.viplev.api.domain.model.Host;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HostRepository extends JpaRepository<Host, UUID> {

    List<Host> findByEnvironmentId(UUID environmentId);
}
