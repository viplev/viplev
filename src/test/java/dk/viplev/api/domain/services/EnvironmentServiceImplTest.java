package dk.viplev.api.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dk.viplev.api.adapter.inbound.rest.dto.EnvironmentDTO;
import dk.viplev.api.adapter.inbound.rest.mapper.EnvironmentMapper;
import dk.viplev.api.config.security.JwtIssuer;
import dk.viplev.api.domain.exception.NotFoundException;
import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.inbound.AuthService;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;
import dk.viplev.api.port.outbound.db.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class EnvironmentServiceImplTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtIssuer jwtIssuer;

    @Mock
    private AuthService authService;

    private final EnvironmentMapper environmentMapper = Mappers.getMapper(EnvironmentMapper.class);

    private EnvironmentServiceImpl environmentService;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        environmentService = new EnvironmentServiceImpl(
                environmentRepository, userRepository, jwtIssuer, authService, environmentMapper);
        when(authService.getAuthenticatedUserId()).thenReturn(ownerId);
    }

    @Test
    void shouldListEnvironmentsForOwner() {
        Environment env = createEnvironment("Test Env", "docker");
        when(environmentRepository.findByOwnerId(ownerId)).thenReturn(List.of(env));

        List<EnvironmentDTO> result = environmentService.listEnvironments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Env");
    }

    @Test
    void shouldCreateEnvironment() {
        User owner = new User();
        owner.setId(ownerId);
        when(userRepository.getReferenceById(ownerId)).thenReturn(owner);
        when(jwtIssuer.issueEnvironmentToken(any(UUID.class), any())).thenReturn("test-token");
        when(environmentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Environment env = invocation.getArgument(0);
            env.setCreatedAt(LocalDateTime.now());
            env.setUpdatedAt(LocalDateTime.now());
            return env;
        });

        EnvironmentDTO request = new EnvironmentDTO("New Env", EnvironmentDTO.TypeEnum.DOCKER);

        EnvironmentDTO result = environmentService.createEnvironment(request);

        assertThat(result.getName()).isEqualTo("New Env");
        assertThat(result.getType()).isEqualTo(EnvironmentDTO.TypeEnum.DOCKER);
        assertThat(result.getToken()).isEqualTo("test-token");
        assertThat(result.getAgentCommand()).contains("docker run");
        assertThat(result.getAgentCommand()).contains("test-token");
    }

    @Test
    void shouldGetEnvironmentById() {
        Environment env = createEnvironment("My Env", "kubernetes");
        when(environmentRepository.findByIdAndOwnerId(env.getId(), ownerId))
                .thenReturn(Optional.of(env));

        EnvironmentDTO result = environmentService.getEnvironment(env.getId());

        assertThat(result.getName()).isEqualTo("My Env");
        assertThat(result.getType()).isEqualTo(EnvironmentDTO.TypeEnum.KUBERNETES);
    }

    @Test
    void shouldThrowNotFoundWhenEnvironmentDoesNotExist() {
        UUID envId = UUID.randomUUID();
        when(environmentRepository.findByIdAndOwnerId(envId, ownerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> environmentService.getEnvironment(envId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Environment not found");
    }

    @Test
    void shouldUpdateEnvironment() {
        Environment env = createEnvironment("Old Name", "docker");
        when(environmentRepository.findByIdAndOwnerId(env.getId(), ownerId))
                .thenReturn(Optional.of(env));
        when(environmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EnvironmentDTO request = new EnvironmentDTO("New Name", EnvironmentDTO.TypeEnum.KUBERNETES);
        request.setDescription("Updated desc");

        EnvironmentDTO result = environmentService.updateEnvironment(env.getId(), request);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getType()).isEqualTo(EnvironmentDTO.TypeEnum.KUBERNETES);
        assertThat(result.getDescription()).isEqualTo("Updated desc");
    }

    @Test
    void shouldDeleteEnvironment() {
        Environment env = createEnvironment("To Delete", "docker");
        when(environmentRepository.findByIdAndOwnerId(env.getId(), ownerId))
                .thenReturn(Optional.of(env));

        environmentService.deleteEnvironment(env.getId());

        verify(environmentRepository).delete(env);
    }

    @Test
    void shouldMapAgentLastSeenAt() {
        LocalDateTime lastSeen = LocalDateTime.of(2025, 6, 15, 10, 30, 0);
        Environment env = createEnvironment("Agent Env", "docker");
        env.setAgentLastSeenAt(lastSeen);
        when(environmentRepository.findByIdAndOwnerId(env.getId(), ownerId))
                .thenReturn(Optional.of(env));

        EnvironmentDTO result = environmentService.getEnvironment(env.getId());

        assertThat(result.getAgentLastSeenAt()).isEqualTo(lastSeen);
    }

    @Test
    void shouldReturnNullAgentLastSeenAtForNewEnvironment() {
        Environment env = createEnvironment("New Env", "docker");
        when(environmentRepository.findByIdAndOwnerId(env.getId(), ownerId))
                .thenReturn(Optional.of(env));

        EnvironmentDTO result = environmentService.getEnvironment(env.getId());

        assertThat(result.getAgentLastSeenAt()).isNull();
    }

    @Test
    void shouldGenerateDockerAgentCommand() {
        Environment env = createEnvironment("Docker Env", "docker");
        env.setToken("my-token");
        when(environmentRepository.findByIdAndOwnerId(env.getId(), ownerId))
                .thenReturn(Optional.of(env));

        EnvironmentDTO result = environmentService.getEnvironment(env.getId());

        assertThat(result.getAgentCommand())
                .isEqualTo("docker run -d"
                        + " --pull always"
                        + " --restart unless-stopped"
                        + " --add-host=host.docker.internal:host-gateway"
                        + " --name viplev-agent"
                        + " -e VIPLEV_URL=https://api.viplev.ringhus.dk"
                        + " -e VIPLEV_TOKEN=my-token"
                        + " -e VIPLEV_ENVIRONMENT_ID=" + env.getId()
                        + " -e VIPLEV_CADVISOR_IMAGE=gcr.io/cadvisor/cadvisor:v0.55.1"
                        + " -e VIPLEV_NODE_EXPORTER_IMAGE=prom/node-exporter:v1.9.0"
                        + " -e VIPLEV_K6_IMAGE=grafana/k6:0.53.0"
                        + " -e VIPLEV_K6_TIMEOUT_MS=300000"
                        + " -e AGENT_MESSAGE_POLLING_ENABLED=true"
                        + " -v /var/run/docker.sock:/var/run/docker.sock"
                        + " ghcr.io/viplev/agent:latest");
    }

    @Test
    void shouldGenerateKubernetesAgentCommand() {
        Environment env = createEnvironment("K8s Env", "kubernetes");
        env.setToken("k8s-token");
        when(environmentRepository.findByIdAndOwnerId(env.getId(), ownerId))
                .thenReturn(Optional.of(env));

        EnvironmentDTO result = environmentService.getEnvironment(env.getId());

        assertThat(result.getAgentCommand()).contains("kubectl apply");
        assertThat(result.getAgentCommand()).contains("k8s-token");
    }

    private Environment createEnvironment(String name, String type) {
        Environment env = new Environment();
        env.setId(UUID.randomUUID());
        env.setName(name);
        env.setType(type);
        env.setToken("test-token-" + UUID.randomUUID());
        env.setCreatedAt(LocalDateTime.now());
        env.setUpdatedAt(LocalDateTime.now());

        User owner = new User();
        owner.setId(ownerId);
        env.setOwner(owner);

        return env;
    }
}
