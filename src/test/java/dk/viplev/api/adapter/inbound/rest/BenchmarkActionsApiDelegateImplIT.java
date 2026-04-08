package dk.viplev.api.adapter.inbound.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class BenchmarkActionsApiDelegateImplIT {

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
    private String user2Token;
    private String user1EnvironmentId;
    private String benchmarkId;

    @BeforeEach
    void setUp() throws Exception {
        user1Token = loginAndGetToken("user1@viplev.dk", "password");
        user2Token = loginAndGetToken("user2@viplev.dk", "password");
        user1EnvironmentId = createEnvironment(user1Token, "Actions Test Env", "docker");
        benchmarkId = createBenchmark(user1Token, user1EnvironmentId, "Actions Test Benchmark");
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String loginJson = objectMapper.writeValueAsString(
                java.util.Map.of("email", email, "password", password));

        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private String createEnvironment(String token, String name, String type) throws Exception {
        String json = objectMapper.writeValueAsString(
                java.util.Map.of("name", name, "type", type));

        MvcResult result = mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private String createBenchmark(String token, String environmentId, String name) throws Exception {
        String json = objectMapper.writeValueAsString(
                java.util.Map.of("name", name));

        MvcResult result = mockMvc.perform(post("/v1/environments/" + environmentId + "/benchmarks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private UUID createBenchmarkRunDirectly(UUID benchmarkUuid, String userEmail, BenchmarkRunStatus status) {
        Benchmark benchmark = benchmarkRepository.findById(benchmarkUuid).orElseThrow();
        User user = userRepository.findByEmail(userEmail).orElseThrow();

        BenchmarkRun run = new BenchmarkRun();
        run.setBenchmark(benchmark);
        run.setStartedByUser(user);
        run.setStatus(status);
        return benchmarkRunRepository.saveAndFlush(run).getId();
    }

    private String startUrl(String environmentId, String benchmarkId) {
        return "/v1/environments/" + environmentId + "/benchmarks/" + benchmarkId + "/start";
    }

    private String stopUrl(String environmentId, String benchmarkId, String runId) {
        return "/v1/environments/" + environmentId + "/benchmarks/" + benchmarkId + "/runs/" + runId + "/stop";
    }

    // --- Start tests ---

    @Test
    void shouldStartBenchmark() throws Exception {
        mockMvc.perform(post(startUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.benchmarkId").value(benchmarkId))
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING_START"));
    }

    @Test
    void shouldReturn409WhenActiveRunExistsWithPendingStart() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);

        mockMvc.perform(post(startUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn409WhenActiveRunExistsWithStarted() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(startUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn409WhenActiveRunExistsWithPendingStop() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk", BenchmarkRunStatus.PENDING_STOP);

        mockMvc.perform(post(startUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAllowStartAfterFinishedRun() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk", BenchmarkRunStatus.FINISHED);

        mockMvc.perform(post(startUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_START"));
    }

    // --- Stop tests ---

    @Test
    void shouldStopStartedRun() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        UUID runId = createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(stopUrl(user1EnvironmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benchmarkId").value(benchmarkId))
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_STOP"));
    }

    @Test
    void shouldStopPendingStartRun() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        UUID runId = createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);

        mockMvc.perform(post(stopUrl(user1EnvironmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));

        BenchmarkRun run = benchmarkRunRepository.findById(runId).orElseThrow();
        assertNotNull(run.getFinishedAt());
    }

    @Test
    void shouldReturn409WhenStoppingStoppedRun() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        UUID runId = createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk", BenchmarkRunStatus.STOPPED);

        mockMvc.perform(post(stopUrl(user1EnvironmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn409WhenStoppingFinishedRun() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        UUID runId = createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk", BenchmarkRunStatus.FINISHED);

        mockMvc.perform(post(stopUrl(user1EnvironmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn409WhenStoppingFailedRun() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        UUID runId = createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk", BenchmarkRunStatus.FAILED);

        mockMvc.perform(post(stopUrl(user1EnvironmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn404WhenStoppingNonExistentRun() throws Exception {
        mockMvc.perform(post(stopUrl(user1EnvironmentId, benchmarkId, UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    // --- Ownership tests ---

    @Test
    void shouldEnforceOwnershipOnStart() throws Exception {
        mockMvc.perform(post(startUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldEnforceOwnershipOnStop() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        UUID runId = createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(stopUrl(user1EnvironmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    // --- 404 tests ---

    @Test
    void shouldReturn404ForNonExistentEnvironment() throws Exception {
        mockMvc.perform(post(startUrl(UUID.randomUUID().toString(), benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForNonExistentBenchmark() throws Exception {
        mockMvc.perform(post(startUrl(user1EnvironmentId, UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    // --- Auth tests ---

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(post(startUrl(user1EnvironmentId, benchmarkId)))
                .andExpect(status().isUnauthorized());
    }
}
