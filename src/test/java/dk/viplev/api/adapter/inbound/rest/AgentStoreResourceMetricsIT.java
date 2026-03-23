package dk.viplev.api.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.Host;
import dk.viplev.api.domain.model.Service;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.HostRepository;
import dk.viplev.api.port.outbound.db.MetricResourceHostRepository;
import dk.viplev.api.port.outbound.db.MetricResourceServiceRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import dk.viplev.api.port.outbound.db.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import dk.viplev.api.domain.model.Environment;
import dk.viplev.api.port.outbound.db.EnvironmentRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class AgentStoreResourceMetricsIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BenchmarkRunRepository benchmarkRunRepository;
    @Autowired private BenchmarkRepository benchmarkRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private HostRepository hostRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private EnvironmentRepository environmentRepository;
    @Autowired private MetricResourceHostRepository metricResourceHostRepository;
    @Autowired private MetricResourceServiceRepository metricResourceServiceRepository;

    private String environmentId;
    private String environmentToken;
    private String benchmarkId;
    private String machineId;
    private String serviceName;

    @BeforeEach
    void setUp() throws Exception {
        String user1Token = loginAndGetToken("user1@viplev.dk", "password");

        JsonNode env = createEnvironmentAndGetResponse("Metrics Test Env", user1Token);
        environmentId = env.get("id").asText();
        environmentToken = env.get("token").asText();

        benchmarkId = createBenchmark(user1Token, environmentId, "Metrics Benchmark");

        registerServices(environmentId, environmentToken);

        List<Host> hosts = hostRepository.findByEnvironmentId(UUID.fromString(environmentId));
        machineId = hosts.get(0).getMachineId();

        List<Service> services = serviceRepository.findByHostEnvironmentId(UUID.fromString(environmentId));
        serviceName = services.get(0).getServiceName();
    }

    @Test
    void shouldStoreHostMetrics() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);
        long countBefore = metricResourceHostRepository.count();

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricsBody(hostMetric(machineId), null)))
                .andExpect(status().isCreated());

        assertThat(metricResourceHostRepository.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void shouldStoreServiceMetrics() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);
        long countBefore = metricResourceServiceRepository.count();

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricsBody(hostMetric(machineId), List.of(serviceMetric(serviceName)))))
                .andExpect(status().isCreated());

        assertThat(metricResourceServiceRepository.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void shouldStoreMixedHostAndServiceMetrics() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);
        long hostCountBefore = metricResourceHostRepository.count();
        long serviceCountBefore = metricResourceServiceRepository.count();

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricsBody(hostMetric(machineId), List.of(serviceMetric(serviceName)))))
                .andExpect(status().isCreated());

        assertThat(metricResourceHostRepository.count()).isEqualTo(hostCountBefore + 1);
        assertThat(metricResourceServiceRepository.count()).isEqualTo(serviceCountBefore + 1);
    }

    @Test
    void shouldReturn404ForInvalidMachineId() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricsBody(hostMetric("nonexistent-machine-id"), null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForInvalidServiceName() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricsBody(hostMetric(machineId), List.of(serviceMetric("nonexistent-service")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForNonExistentRun() throws Exception {
        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricsBody(hostMetric(machineId), null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400WhenRunNotStarted() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.PENDING_START);

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricsBody(hostMetric(machineId), null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenAgentAccessesOtherEnvironment() throws Exception {
        String user2Token = loginAndGetToken("user2@viplev.dk", "password");
        JsonNode env2 = createEnvironmentAndGetResponse("Other Metrics Env", user2Token);
        String env2Id = env2.get("id").asText();
        String benchmark2Id = createBenchmark(user2Token, env2Id, "Other Benchmark");
        UUID runId = createRunDirectly(UUID.fromString(benchmark2Id), "user2@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(metricsUrl(env2Id, benchmark2Id, runId.toString()))
                        .header("Authorization", "Bearer " + environmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricsBody(hostMetric(machineId), null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        UUID runId = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        mockMvc.perform(post(metricsUrl(environmentId, benchmarkId, runId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricsBody(hostMetric(machineId), null)))
                .andExpect(status().isUnauthorized());
    }

    // --- Helpers ---

    private String metricsUrl(String envId, String bmId, String runId) {
        return "/v1/environments/" + envId + "/benchmarks/" + bmId + "/runs/" + runId + "/metrics/resource";
    }

    private String metricsBody(Map<String, Object> host, List<Map<String, Object>> services) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("host", host);
        if (services != null) body.put("services", services);
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> hostMetric(String machineId) {
        return Map.of(
                "machineId", machineId,
                "metrics", List.of(Map.of(
                        "collectedAt", "2025-01-15T10:00:00",
                        "cpuPercentage", 45.5,
                        "memoryUsageBytes", 1073741824.0,
                        "memoryLimitBytes", 2147483648.0,
                        "networkInBytes", 500000.0,
                        "networkOutBytes", 250000.0,
                        "blockInBytes", 100000.0,
                        "blockOutBytes", 50000.0
                ))
        );
    }

    private Map<String, Object> serviceMetric(String serviceName) {
        return Map.of(
                "serviceName", serviceName,
                "metrics", List.of(Map.of(
                        "collectedAt", "2025-01-15T10:00:00",
                        "cpuPercentage", 30.2,
                        "memoryUsageBytes", 536870912.0,
                        "memoryLimitBytes", 1073741824.0,
                        "networkInBytes", 200000.0,
                        "networkOutBytes", 100000.0,
                        "blockInBytes", 50000.0,
                        "blockOutBytes", 25000.0
                ))
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

    private void registerServices(String envId, String envToken) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "host", Map.of(
                        "name", "metrics-test-host",
                        "machineId", "metrics-machine-" + UUID.randomUUID(),
                        "os", "Linux",
                        "ipAddress", "192.168.1.100"
                ),
                "services", List.of(
                        Map.of("serviceName", "metrics-test-svc-" + UUID.randomUUID(), "imageName", "nginx:latest")
                )
        ));
        mockMvc.perform(post("/v1/environments/" + envId + "/services")
                        .header("Authorization", "Bearer " + envToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
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
