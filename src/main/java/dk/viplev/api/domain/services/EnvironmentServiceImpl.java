package dk.viplev.api.domain.services;

import dk.viplev.api.adapter.inbound.rest.dto.EnvironmentDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.EnvironmentMapper;
import dk.viplev.api.config.security.JwtIssuer;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.inbound.EnvironmentService;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;
import dk.viplev.api.port.outbound.db.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnvironmentServiceImpl implements EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final UserRepository userRepository;
    private final JwtIssuer jwtIssuer;
    private final AuthService authService;
    private final EnvironmentMapper environmentMapper;

    @Override
    public List<EnvironmentDTO> listEnvironments() {
        UUID ownerId = authService.getAuthenticatedUserId();
        return environmentRepository.findByOwnerId(ownerId).stream()
                .map(environmentMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public EnvironmentDTO createEnvironment(EnvironmentDTO request) {
        UUID ownerId = authService.getAuthenticatedUserId();
        User owner = userRepository.getReferenceById(ownerId);

        Environment environment = new Environment();
        environment.setName(request.getName());
        environment.setDescription(request.getDescription());
        environment.setType(request.getType().getValue());
        environment.setOwner(owner);

        Environment saved = environmentRepository.saveAndFlush(environment);

        String token = jwtIssuer.issueEnvironmentToken(saved.getId(), ownerId);
        saved.setToken(token);

        return environmentMapper.toDto(saved);
    }

    @Override
    public EnvironmentDTO getEnvironment(UUID environmentId) {
        UUID ownerId = authService.getAuthenticatedUserId();
        Environment environment = findByIdAndOwner(environmentId, ownerId);
        return environmentMapper.toDto(environment);
    }

    @Override
    public EnvironmentDTO updateEnvironment(UUID environmentId, EnvironmentDTO request) {
        UUID ownerId = authService.getAuthenticatedUserId();
        Environment environment = findByIdAndOwner(environmentId, ownerId);

        environment.setName(request.getName());
        environment.setDescription(request.getDescription());
        environment.setType(request.getType().getValue());

        Environment saved = environmentRepository.save(environment);
        return environmentMapper.toDto(saved);
    }

    @Override
    public void deleteEnvironment(UUID environmentId) {
        UUID ownerId = authService.getAuthenticatedUserId();
        Environment environment = findByIdAndOwner(environmentId, ownerId);
        environmentRepository.delete(environment);
    }

    private Environment findByIdAndOwner(UUID environmentId, UUID ownerId) {
        return environmentRepository.findByIdAndOwnerId(environmentId, ownerId)
                .orElseThrow(() -> new NotFoundException("Environment not found"));
    }
}
