package dk.viplev.api.adapter.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

@SpringBootTest
@AutoConfigureMockMvc
class BenchmarkApiDelegateImplIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private BenchmarkRunRepository benchmarkRunRepository;

    @Autowired
    private UserRepository userRepository;

    private String user1Token;
    private String user2Token;
    private String user1EnvironmentId;

    @BeforeEach
    void setUp() throws Exception {
        user1Token = loginAndGetToken("user1@viplev.dk", "password");
        user2Token = loginAndGetToken("user2@viplev.dk", "password");
        user1EnvironmentId = createEnvironment(user1Token, "Benchmark Test Env", "docker");
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

    private String benchmarkUrl(String environmentId) {
        return "/v1/environments/" + environmentId + "/benchmarks";
    }

    private String benchmarkUrl(String environmentId, String benchmarkId) {
        return "/v1/environments/" + environmentId + "/benchmarks/" + benchmarkId;
    }

    private String benchmarkJson(String name, String description, String k6Instructions) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("name", name, "description", description, "k6Instructions", k6Instructions));
    }

    @Test
    void shouldPerformFullCrudFlow() throws Exception {
        // Create
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Load Test", "Test description", "import http from 'k6/http';")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Load Test"))
                .andExpect(jsonPath("$.description").value("Test description"))
                .andExpect(jsonPath("$.k6Instructions").value("import http from 'k6/http';"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // List
        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Load Test')]").exists());

        // Get
        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Load Test"))
                .andExpect(jsonPath("$.id").value(benchmarkId));

        // Update
        mockMvc.perform(put(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Updated Test", "Updated desc", "export default function() {}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Test"))
                .andExpect(jsonPath("$.description").value("Updated desc"))
                .andExpect(jsonPath("$.k6Instructions").value("export default function() {}"));

        // Delete
        mockMvc.perform(delete(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        // Verify gone
        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldEnforceOwnership() throws Exception {
        // User1 creates benchmark
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Private Benchmark", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // User2 cannot list benchmarks in user1's environment
        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());

        // User2 cannot get the benchmark
        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());

        // User2 cannot update the benchmark
        mockMvc.perform(put(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Hacked", "hacked", "hacked")))
                .andExpect(status().isNotFound());

        // User2 cannot delete the benchmark
        mockMvc.perform(delete(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForNonExistentBenchmark() throws Exception {
        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId, "00000000-0000-0000-0000-000000000000"))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForBenchmarkUnderWrongEnvironment() throws Exception {
        // Create benchmark in user1's environment
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Env1 Benchmark", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Create a second environment for user1
        String env2Id = createEnvironment(user1Token, "Second Env", "docker");

        // Try to get benchmark under wrong environment
        mockMvc.perform(get(benchmarkUrl(env2Id, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId)))
                .andExpect(status().isUnauthorized());
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

    @Test
    void shouldDeleteBenchmarkWithRuns() throws Exception {
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Benchmark With Run", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String bmId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        createBenchmarkRunDirectly(UUID.fromString(bmId), "user1@viplev.dk");

        mockMvc.perform(delete(benchmarkUrl(user1EnvironmentId, bmId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId, bmId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }
}
