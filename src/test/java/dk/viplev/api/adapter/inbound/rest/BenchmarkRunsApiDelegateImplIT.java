package dk.viplev.api.adapter.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.UserRepository;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class BenchmarkRunsApiDelegateImplIT {

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
        user1EnvironmentId = createEnvironment(user1Token, "BenchmarkRun Test Env", "docker");
        benchmarkId = createBenchmark(user1Token, user1EnvironmentId, "Test Benchmark");
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

    private UUID createBenchmarkRunDirectly(UUID benchmarkUuid, String userEmail) {
        Benchmark benchmark = benchmarkRepository.findById(benchmarkUuid).orElseThrow();
        User user = userRepository.findByEmail(userEmail).orElseThrow();

        BenchmarkRun run = new BenchmarkRun();
        run.setBenchmark(benchmark);
        run.setStartedByUser(user);
        run.setStatus(BenchmarkRunStatus.PENDING_START);
        return benchmarkRunRepository.saveAndFlush(run).getId();
    }

    private String runsUrl(String environmentId, String benchmarkId) {
        return "/v1/environments/" + environmentId + "/benchmarks/" + benchmarkId + "/runs";
    }

    private String runUrl(String environmentId, String benchmarkId, String runId) {
        return "/v1/environments/" + environmentId + "/benchmarks/" + benchmarkId + "/runs/" + runId;
    }

    @Test
    void shouldListRunsForBenchmark() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk");
        createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk");

        mockMvc.perform(get(runsUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].status").value("PENDING_START"))
                .andExpect(jsonPath("$[0].startedBy").isNotEmpty())
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenNoRuns() throws Exception {
        mockMvc.perform(get(runsUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldDeleteRun() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        UUID runId = createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk");

        mockMvc.perform(delete(runUrl(user1EnvironmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get(runsUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentRun() throws Exception {
        mockMvc.perform(delete(runUrl(user1EnvironmentId, benchmarkId, UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldEnforceOwnership() throws Exception {
        UUID benchmarkUuid = UUID.fromString(benchmarkId);
        UUID runId = createBenchmarkRunDirectly(benchmarkUuid, "user1@viplev.dk");

        // User2 cannot list runs in user1's environment
        mockMvc.perform(get(runsUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());

        // User2 cannot delete run in user1's environment
        mockMvc.perform(delete(runUrl(user1EnvironmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForRunsUnderNonExistentBenchmark() throws Exception {
        mockMvc.perform(get(runsUrl(user1EnvironmentId, UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForRunsUnderNonExistentEnvironment() throws Exception {
        mockMvc.perform(get(runsUrl(UUID.randomUUID().toString(), benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get(runsUrl(user1EnvironmentId, benchmarkId)))
                .andExpect(status().isUnauthorized());
    }
}
