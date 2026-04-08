package dk.viplev.api.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;
import dk.viplev.api.port.outbound.db.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class AgentListMessagesIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BenchmarkRunRepository benchmarkRunRepository;
    @Autowired private BenchmarkRepository benchmarkRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EnvironmentRepository environmentRepository;

    private String user1Token;
    private String user2Token;
    private String environmentId;
    private String environmentToken;
    private String benchmarkId;

    @BeforeEach
    void setUp() throws Exception {
        user1Token = loginAndGetToken("user1@viplev.dk", "password");
        user2Token = loginAndGetToken("user2@viplev.dk", "password");

        JsonNode env = createEnvironmentAndGetResponse("Agent Message Queue Env", user1Token);
        environmentId = env.get("id").asText();
        environmentToken = env.get("token").asText();

        benchmarkId = createBenchmark(user1Token, environmentId, "Agent Queue Benchmark");
    }

    @Test
    void shouldReturnPendingStopBeforePendingStartAndOnlyOneMessage() throws Exception {
        UUID pendingStartRunId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);
        Thread.sleep(5);
        UUID pendingStopRunId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_STOP);

        mockMvc.perform(get(messageUrl(environmentId))
                        .header("Authorization", "Bearer " + environmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].benchmarkId").value(benchmarkId))
                .andExpect(jsonPath("$[0].runId").value(pendingStopRunId.toString()))
                .andExpect(jsonPath("$[0].messageType").value("PENDING_STOP"));

        assertThat(pendingStartRunId).isNotEqualTo(pendingStopRunId);
    }

    @Test
    void shouldReturnOldestPendingStopMessage() throws Exception {
        String benchmark2Id = createBenchmark(user1Token, environmentId, "Agent Queue Benchmark 2");

        UUID oldestStopRunId = createRunDirectly(UUID.fromString(benchmark2Id), "user1@viplev.dk", BenchmarkRunStatus.PENDING_STOP);
        Thread.sleep(5);
        createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_STOP);

        mockMvc.perform(get(messageUrl(environmentId))
                        .header("Authorization", "Bearer " + environmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].runId").value(oldestStopRunId.toString()))
                .andExpect(jsonPath("$[0].messageType").value("PENDING_STOP"));
    }

    @Test
    void shouldReturnOldestPendingStartWhenNoPendingStop() throws Exception {
        String benchmark2Id = createBenchmark(user1Token, environmentId, "Agent Queue Benchmark 3");

        UUID oldestStartRunId = createRunDirectly(UUID.fromString(benchmark2Id), "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);
        Thread.sleep(5);
        createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);

        mockMvc.perform(get(messageUrl(environmentId))
                        .header("Authorization", "Bearer " + environmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].runId").value(oldestStartRunId.toString()))
                .andExpect(jsonPath("$[0].messageType").value("PENDING_START"));
    }

    @Test
    void shouldReturnEmptyListWhenNoPendingRuns() throws Exception {
        createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(get(messageUrl(environmentId))
                        .header("Authorization", "Bearer " + environmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldUpdateAgentLastSeenAtOnPoll() throws Exception {
        Environment before = environmentRepository.findById(UUID.fromString(environmentId)).orElseThrow();
        assertThat(before.getAgentLastSeenAt()).isNull();

        mockMvc.perform(get(messageUrl(environmentId))
                        .header("Authorization", "Bearer " + environmentToken))
                .andExpect(status().isOk());

        Environment after = environmentRepository.findById(UUID.fromString(environmentId)).orElseThrow();
        assertThat(after.getAgentLastSeenAt()).isNotNull();
        assertThat(after.getAgentLastSeenAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void shouldOnlyReturnMessagesForRequestedEnvironment() throws Exception {
        UUID env1RunId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);

        JsonNode env2 = createEnvironmentAndGetResponse("Other Agent Env", user2Token);
        String env2Id = env2.get("id").asText();
        String benchmark2Id = createBenchmark(user2Token, env2Id, "Other Benchmark");
        createRunDirectly(UUID.fromString(benchmark2Id), "user2@viplev.dk", BenchmarkRunStatus.PENDING_STOP);

        mockMvc.perform(get(messageUrl(environmentId))
                        .header("Authorization", "Bearer " + environmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].runId").value(env1RunId.toString()))
                .andExpect(jsonPath("$[0].messageType").value("PENDING_START"));
    }

    @Test
    void shouldReturn404WhenAgentAccessesOtherEnvironment() throws Exception {
        JsonNode env2 = createEnvironmentAndGetResponse("Other Agent Env 2", user2Token);
        String env2Id = env2.get("id").asText();
        String benchmark2Id = createBenchmark(user2Token, env2Id, "Other Benchmark 2");
        createRunDirectly(UUID.fromString(benchmark2Id), "user2@viplev.dk", BenchmarkRunStatus.PENDING_STOP);

        mockMvc.perform(get(messageUrl(env2Id))
                        .header("Authorization", "Bearer " + environmentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get(messageUrl(environmentId)))
                .andExpect(status().isUnauthorized());
    }

    private String messageUrl(String envId) {
        return "/v1/environments/" + envId + "/message";
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String loginJson = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode createEnvironmentAndGetResponse(String name, String token) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("name", name, "type", "docker"));
        MvcResult result = mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createBenchmark(String token, String envId, String name) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("name", name));
        MvcResult result = mockMvc.perform(post("/v1/environments/" + envId + "/benchmarks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private UUID createRunDirectly(UUID benchmarkUuid, String userEmail, BenchmarkRunStatus status) {
        Benchmark benchmark = benchmarkRepository.findById(benchmarkUuid).orElseThrow();
        User user = userRepository.findByEmail(userEmail).orElseThrow();

        BenchmarkRun run = new BenchmarkRun();
        run.setBenchmark(benchmark);
        run.setStartedByUser(user);
        run.setStatus(status);
        return benchmarkRunRepository.saveAndFlush(run).getId();
    }
}
