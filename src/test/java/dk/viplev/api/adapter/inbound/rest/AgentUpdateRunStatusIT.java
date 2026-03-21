package dk.viplev.api.adapter.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.UserRepository;

import java.util.Map;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class AgentUpdateRunStatusIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BenchmarkRunRepository benchmarkRunRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private UserRepository userRepository;

    private String user1Token;
    private String environmentId;
    private String environmentToken;
    private String benchmarkId;

    @BeforeEach
    void setUp() throws Exception {
        user1Token = loginAndGetToken("user1@viplev.dk", "password");

        JsonNode env = createEnvironmentAndGetResponse("Agent Status Test Env", user1Token);
        environmentId = env.get("id").asText();
        environmentToken = env.get("token").asText();

        benchmarkId = createBenchmark(user1Token, environmentId, "Agent Status Benchmark");
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String loginJson = objectMapper.writeValueAsString(
                Map.of("email", email, "password", password));

        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
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

    private String createBenchmark(String token, String environmentId, String name) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("name", name));

        MvcResult result = mockMvc.perform(post("/v1/environments/" + environmentId + "/benchmarks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
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

    private String statusUrl(String environmentId, String benchmarkId, String runId) {
        return "/v1/environments/" + environmentId + "/benchmarks/" + benchmarkId + "/runs/" + runId + "/status";
    }

    private String statusBody(String status) throws Exception {
        return objectMapper.writeValueAsString(Map.of("status", status));
    }

    private String statusBodyWithReason(String status, String reason) throws Exception {
        return objectMapper.writeValueAsString(Map.of("status", status, "statusReason", reason));
    }

    // --- Valid transitions ---

    @Test
    void shouldTransitionFromPendingStartToStarted() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("STARTED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());
    }

    @Test
    void shouldTransitionFromStartedToFinished() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("FINISHED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());
    }

    @Test
    void shouldTransitionFromStartedToFailed() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBodyWithReason("FAILED", "K6 script error")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.statusReason").value("K6 script error"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());
    }

    @Test
    void shouldTransitionFromPendingStopToStopped() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_STOP);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("STOPPED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());
    }

    @Test
    void shouldTransitionFromPendingStopToFailed() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_STOP);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBodyWithReason("FAILED", "Agent crash")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.statusReason").value("Agent crash"));
    }

    @Test
    void shouldTransitionFromPendingStartToFailed() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBodyWithReason("FAILED", "Could not start K6")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    // --- Invalid transitions ---

    @Test
    void shouldReturn400ForInvalidTransitionPendingStartToFinished() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("FINISHED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400ForInvalidTransitionStartedToStopped() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("STOPPED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400ForTransitionFromTerminalStateStopped() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STOPPED);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("STARTED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400ForTransitionFromTerminalStateFailed() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.FAILED);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("STARTED")))
                .andExpect(status().isBadRequest());
    }

    // --- statusReason validation ---

    @Test
    void shouldReturn400WhenFailedWithoutStatusReason() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("FAILED")))
                .andExpect(status().isBadRequest());
    }

    // --- Environment access ---

    @Test
    void shouldReturn404WhenAgentAccessesOtherEnvironment() throws Exception {
        String user2Token = loginAndGetToken("user2@viplev.dk", "password");
        JsonNode env2 = createEnvironmentAndGetResponse("Other Agent Env", user2Token);
        String env2Id = env2.get("id").asText();
        String benchmark2Id = createBenchmark(user2Token, env2Id, "Other Benchmark");

        UUID runId = createRunDirectly(UUID.fromString(benchmark2Id), "user2@viplev.dk", BenchmarkRunStatus.PENDING_START);

        // Use env1's token to access env2's run
        mockMvc.perform(post(statusUrl(env2Id, benchmark2Id, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("STARTED")))
                .andExpect(status().isNotFound());
    }

    // --- Not found ---

    @Test
    void shouldReturn404ForNonExistentRun() throws Exception {
        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("STARTED")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForNonExistentBenchmark() throws Exception {
        mockMvc.perform(post(statusUrl(environmentId, UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("STARTED")))
                .andExpect(status().isNotFound());
    }

    // --- Auth ---

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);

        mockMvc.perform(post(statusUrl(environmentId, benchmarkId, runId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("STARTED")))
                .andExpect(status().isUnauthorized());
    }
}
