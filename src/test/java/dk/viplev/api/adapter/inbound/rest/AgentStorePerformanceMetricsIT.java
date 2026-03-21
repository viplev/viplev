package dk.viplev.api.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.MetricK6HttpRepository;
import dk.viplev.api.port.outbound.db.MetricK6VusRepository;
import dk.viplev.api.port.outbound.db.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class AgentStorePerformanceMetricsIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BenchmarkRunRepository benchmarkRunRepository;
    @Autowired private BenchmarkRepository benchmarkRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MetricK6HttpRepository metricK6HttpRepository;
    @Autowired private MetricK6VusRepository metricK6VusRepository;

    private String environmentId;
    private String environmentToken;
    private String benchmarkId;

    @BeforeEach
    void setUp() throws Exception {
        String user1Token = loginAndGetToken("user1@viplev.dk", "password");

        JsonNode env = createEnvironmentAndGetResponse("Perf Metrics Test Env", user1Token);
        environmentId = env.get("id").asText();
        environmentToken = env.get("token").asText();

        benchmarkId = createBenchmark(user1Token, environmentId, "Perf Metrics Benchmark");
    }

    @Test
    void shouldStoreHttpMetrics() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);
        long countBefore = metricK6HttpRepository.count();

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(performanceBody(List.of(httpMetric()), null)))
                .andExpect(status().isCreated());

        assertThat(metricK6HttpRepository.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void shouldStoreVusMetrics() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);
        long countBefore = metricK6VusRepository.count();

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(performanceBody(null, List.of(vusMetric()))))
                .andExpect(status().isCreated());

        assertThat(metricK6VusRepository.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void shouldStoreMixedHttpAndVusMetrics() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);
        long httpCountBefore = metricK6HttpRepository.count();
        long vusCountBefore = metricK6VusRepository.count();

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(performanceBody(List.of(httpMetric()), List.of(vusMetric()))))
                .andExpect(status().isCreated());

        assertThat(metricK6HttpRepository.count()).isEqualTo(httpCountBefore + 1);
        assertThat(metricK6VusRepository.count()).isEqualTo(vusCountBefore + 1);
    }

    @Test
    void shouldReturn404ForNonExistentRun() throws Exception {
        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(performanceBody(List.of(httpMetric()), null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400WhenRunNotStarted() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(performanceBody(List.of(httpMetric()), null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenAgentAccessesOtherEnvironment() throws Exception {
        String user2Token = loginAndGetToken("user2@viplev.dk", "password");
        JsonNode env2 = createEnvironmentAndGetResponse("Other Perf Env", user2Token);
        String env2Id = env2.get("id").asText();
        String benchmark2Id = createBenchmark(user2Token, env2Id, "Other Benchmark");
        UUID runId = createRunDirectly(UUID.fromString(benchmark2Id), "user2@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(metricsUrl(env2Id, benchmark2Id, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(performanceBody(List.of(httpMetric()), null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(performanceBody(List.of(httpMetric()), null)))
                .andExpect(status().isUnauthorized());
    }

    // --- Helpers ---

    private String metricsUrl(String envId, String bmId, String runId) {
        return "/v1/environments/" + envId + "/benchmarks/" + bmId + "/runs/" + runId + "/metrics/performance";
    }

    private String performanceBody(List<Map<String, Object>> httpMetrics, List<Map<String, Object>> vusMetrics) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        if (httpMetrics != null) body.put("httpMetrics", httpMetrics);
        if (vusMetrics != null) body.put("vusMetrics", vusMetrics);
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> httpMetric() {
        return Map.of(
                "collectedAt", "2025-01-15T10:00:00",
                "url", "http://localhost:8080/api/test",
                "httpMethod", "GET",
                "httpStatus", 200,
                "expectedStatus", 200,
                "dataReceivedByte", 1024,
                "dataSentByte", 256,
                "httpReqDurationMs", 150,
                "httpReqWaitingMs", 120
        );
    }

    private Map<String, Object> vusMetric() {
        return Map.of(
                "collectedAt", "2025-01-15T10:00:00",
                "vus", 50
        );
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
