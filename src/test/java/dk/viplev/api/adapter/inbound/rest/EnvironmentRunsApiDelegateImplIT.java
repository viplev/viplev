package dk.viplev.api.adapter.inbound.rest;

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

import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.UserRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class EnvironmentRunsApiDelegateImplIT {

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

    @BeforeEach
    void setUp() throws Exception {
        user1Token = loginAndGetToken("user1@viplev.dk", "password");
        user2Token = loginAndGetToken("user2@viplev.dk", "password");
        user1EnvironmentId = createEnvironment(user1Token, "EnvRuns Test Env", "docker");
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

    private UUID createBenchmarkRun(UUID benchmarkUuid, String userEmail, BenchmarkRunStatus status,
                                     LocalDateTime startedAt, LocalDateTime finishedAt) {
        Benchmark benchmark = benchmarkRepository.findById(benchmarkUuid).orElseThrow();
        User user = userRepository.findByEmail(userEmail).orElseThrow();

        BenchmarkRun run = new BenchmarkRun();
        run.setBenchmark(benchmark);
        run.setStartedByUser(user);
        run.setStatus(status);
        run.setStartedAt(startedAt);
        run.setFinishedAt(finishedAt);
        return benchmarkRunRepository.saveAndFlush(run).getId();
    }

    private String environmentRunsUrl(String environmentId) {
        return "/v1/environments/" + environmentId + "/runs";
    }

    @Test
    void shouldReturnEmptyListWhenNoRuns() throws Exception {
        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs").isArray())
                .andExpect(jsonPath("$.runs.length()").value(0))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20))
                .andExpect(jsonPath("$.pagination.totalElements").value(0))
                .andExpect(jsonPath("$.pagination.totalPages").value(0));
    }

    @Test
    void shouldReturnRunsAcrossMultipleBenchmarks() throws Exception {
        String benchmark1Id = createBenchmark(user1Token, user1EnvironmentId, "Benchmark A");
        String benchmark2Id = createBenchmark(user1Token, user1EnvironmentId, "Benchmark B");

        createBenchmarkRun(UUID.fromString(benchmark1Id), "user1@viplev.dk",
                BenchmarkRunStatus.FINISHED, LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 11, 0));
        createBenchmarkRun(UUID.fromString(benchmark2Id), "user1@viplev.dk",
                BenchmarkRunStatus.STARTED, LocalDateTime.of(2026, 1, 2, 10, 0), null);

        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs.length()").value(2))
                .andExpect(jsonPath("$.runs[0].benchmarkId").isNotEmpty())
                .andExpect(jsonPath("$.runs[0].runId").isNotEmpty())
                .andExpect(jsonPath("$.runs[0].status").isNotEmpty())
                .andExpect(jsonPath("$.pagination.totalElements").value(2));
    }

    @Test
    void shouldPaginateCorrectly() throws Exception {
        String benchmarkId = createBenchmark(user1Token, user1EnvironmentId, "Paginate Benchmark");
        UUID benchmarkUuid = UUID.fromString(benchmarkId);

        for (int i = 0; i < 5; i++) {
            createBenchmarkRun(benchmarkUuid, "user1@viplev.dk",
                    BenchmarkRunStatus.FINISHED, LocalDateTime.of(2026, 1, i + 1, 10, 0),
                    LocalDateTime.of(2026, 1, i + 1, 11, 0));
        }

        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs.length()").value(2))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(5))
                .andExpect(jsonPath("$.pagination.totalPages").value(3));

        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .param("page", "2")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs.length()").value(1))
                .andExpect(jsonPath("$.pagination.page").value(2));
    }

    @Test
    void shouldSortByStartedAtDesc() throws Exception {
        String benchmarkId = createBenchmark(user1Token, user1EnvironmentId, "Sort Benchmark");
        UUID benchmarkUuid = UUID.fromString(benchmarkId);

        createBenchmarkRun(benchmarkUuid, "user1@viplev.dk",
                BenchmarkRunStatus.FINISHED, LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 11, 0));
        createBenchmarkRun(benchmarkUuid, "user1@viplev.dk",
                BenchmarkRunStatus.FINISHED, LocalDateTime.of(2026, 3, 1, 10, 0),
                LocalDateTime.of(2026, 3, 1, 11, 0));

        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .param("sort", "startedAt,desc")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs.length()").value(2))
                .andExpect(jsonPath("$.runs[0].startedAt").value("2026-03-01T10:00:00"))
                .andExpect(jsonPath("$.runs[1].startedAt").value("2026-01-01T10:00:00"));
    }

    @Test
    void shouldSortByFinishedAtAsc() throws Exception {
        String benchmarkId = createBenchmark(user1Token, user1EnvironmentId, "Sort Finished Benchmark");
        UUID benchmarkUuid = UUID.fromString(benchmarkId);

        createBenchmarkRun(benchmarkUuid, "user1@viplev.dk",
                BenchmarkRunStatus.FINISHED, LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 2, 1, 11, 0));
        createBenchmarkRun(benchmarkUuid, "user1@viplev.dk",
                BenchmarkRunStatus.FINISHED, LocalDateTime.of(2026, 1, 2, 10, 0),
                LocalDateTime.of(2026, 1, 2, 11, 0));

        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .param("sort", "finishedAt,asc")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs[0].finishedAt").value("2026-01-02T11:00:00"))
                .andExpect(jsonPath("$.runs[1].finishedAt").value("2026-02-01T11:00:00"));
    }

    @Test
    void shouldSortByStatus() throws Exception {
        String benchmarkId = createBenchmark(user1Token, user1EnvironmentId, "Sort Status Benchmark");
        UUID benchmarkUuid = UUID.fromString(benchmarkId);

        createBenchmarkRun(benchmarkUuid, "user1@viplev.dk",
                BenchmarkRunStatus.STARTED, LocalDateTime.of(2026, 1, 1, 10, 0), null);
        createBenchmarkRun(benchmarkUuid, "user1@viplev.dk",
                BenchmarkRunStatus.FINISHED, LocalDateTime.of(2026, 1, 2, 10, 0),
                LocalDateTime.of(2026, 1, 2, 11, 0));

        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .param("sort", "status,asc")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs.length()").value(2));
    }

    @Test
    void shouldReturn404ForOtherUsersEnvironment() throws Exception {
        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForNonExistentEnvironment() throws Exception {
        mockMvc.perform(get(environmentRunsUrl(UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn400ForNegativePage() throws Exception {
        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .param("page", "-1")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400ForZeroSize() throws Exception {
        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .param("size", "0")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400ForSizeExceedingMax() throws Exception {
        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .param("size", "101")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400ForInvalidSortDirection() throws Exception {
        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .param("sort", "status,down")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400ForInvalidSortField() throws Exception {
        mockMvc.perform(get(environmentRunsUrl(user1EnvironmentId))
                        .param("sort", "invalidField,asc")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isBadRequest());
    }
}
