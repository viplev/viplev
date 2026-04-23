package dk.viplev.api.adapter.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.Host;
import dk.viplev.api.domain.model.MetricK6Http;
import dk.viplev.api.domain.model.MetricK6Vus;
import dk.viplev.api.domain.model.MetricResourceHost;
import dk.viplev.api.domain.model.MetricResourceReplica;
import dk.viplev.api.domain.model.ServiceReplica;
import dk.viplev.api.domain.model.Service;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.HostRepository;
import dk.viplev.api.port.outbound.db.MetricK6HttpRepository;
import dk.viplev.api.port.outbound.db.MetricK6VusRepository;
import dk.viplev.api.port.outbound.db.MetricResourceHostRepository;
import dk.viplev.api.port.outbound.db.MetricResourceReplicaRepository;
import dk.viplev.api.port.outbound.db.ServiceReplicaRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class BenchmarkRunDerivedIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BenchmarkRunRepository benchmarkRunRepository;
    @Autowired private BenchmarkRepository benchmarkRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private HostRepository hostRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private MetricK6HttpRepository metricK6HttpRepository;
    @Autowired private MetricK6VusRepository metricK6VusRepository;
    @Autowired private MetricResourceHostRepository metricResourceHostRepository;
    @Autowired private MetricResourceReplicaRepository metricResourceReplicaRepository;
    @Autowired private ServiceReplicaRepository serviceReplicaRepository;

    private String user1Token;
    private String user2Token;
    private String environmentId;
    private String benchmarkId;
    private Host host;
    private Service service;
    private ServiceReplica replica;

    @BeforeEach
    void setUp() throws Exception {
        user1Token = loginAndGetToken("user1@viplev.dk", "password");
        user2Token = loginAndGetToken("user2@viplev.dk", "password");

        JsonNode env = createEnvironmentAndGetResponse("Derived Test Env", user1Token);
        environmentId = env.get("id").asText();
        String environmentToken = env.get("token").asText();

        benchmarkId = createBenchmark(user1Token, environmentId, "Derived Benchmark");

        registerServices(environmentId, environmentToken);

        List<Host> hosts = hostRepository.findByEnvironmentId(UUID.fromString(environmentId));
        host = hosts.get(0);

        List<Service> services = serviceRepository.findByEnvironmentId(UUID.fromString(environmentId));
        service = services.get(0);
        
        // Create a replica for the service
        // Use unique container ID to avoid conflicts across tests (global uniqueness constraint)
        String uniqueContainerId = "test-container-" + UUID.randomUUID();
        replica = new ServiceReplica();
        replica.setService(service);
        replica.setHost(host);
        replica.setContainerId(uniqueContainerId);
        replica.setContainerName(uniqueContainerId);
        replica.setStartedAt(LocalDateTime.now());
        replica.setLastSeenAt(LocalDateTime.now());
        replica = serviceReplicaRepository.saveAndFlush(replica);
    }

    @Test
    void shouldReturnDerivedMetricsWithCorrectAggregations() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");
        LocalDateTime baseTime = LocalDateTime.of(2025, 1, 15, 10, 0, 0);

        // Seed 10 HTTP metrics in "group-a"
        for (int i = 0; i < 10; i++) {
            metricK6HttpRepository.saveAndFlush(new MetricK6Http(
                    run, baseTime.plusSeconds(i), "http://test.com/api",
                    "GET", "group-a", 200, 200,
                    1000, 500, 10 + i, 5 + i));
        }

        // Seed 2 VUS metrics
        metricK6VusRepository.saveAndFlush(new MetricK6Vus(run, baseTime, 5));
        metricK6VusRepository.saveAndFlush(new MetricK6Vus(run, baseTime.plusSeconds(5), 15));

        // Seed 2 host resource metrics
        metricResourceHostRepository.saveAndFlush(new MetricResourceHost(
                run, host, baseTime, 40.0, 1000L, 2000L, 100L, 50L, 20L, 10L));
        metricResourceHostRepository.saveAndFlush(new MetricResourceHost(
                run, host, baseTime.plusSeconds(5), 60.0, 1500L, 2000L, 200L, 100L, 30L, 15L));

        // Seed 2 replica resource metrics
        metricResourceReplicaRepository.saveAndFlush(new MetricResourceReplica(
                run, replica, baseTime, 20.0, 500L, 1000L, 50L, 25L, 10L, 5L));
        metricResourceReplicaRepository.saveAndFlush(new MetricResourceReplica(
                run, replica, baseTime.plusSeconds(5), 30.0, 700L, 1000L, 80L, 40L, 15L, 8L));

        mockMvc.perform(get(runUrl(environmentId, benchmarkId, run.getId().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.id").value(run.getId().toString()))
                .andExpect(jsonPath("$.run.status").value("PENDING_START"))
                // HTTP aggregations
                .andExpect(jsonPath("$.http.length()").value(1))
                .andExpect(jsonPath("$.http[0].requestGroup").value("group-a"))
                .andExpect(jsonPath("$.http[0].totalRequests").value(10))
                .andExpect(jsonPath("$.http[0].errorRate").value(0.0))
                .andExpect(jsonPath("$.http[0].totalDataReceivedBytes").value(10000))
                .andExpect(jsonPath("$.http[0].totalDataSentBytes").value(5000))
                .andExpect(jsonPath("$.http[0].duration.min").value(10.0))
                .andExpect(jsonPath("$.http[0].duration.max").value(19.0))
                .andExpect(jsonPath("$.http[0].duration.percentiles.p90").isNumber())
                .andExpect(jsonPath("$.http[0].duration.percentiles.p95").isNumber())
                .andExpect(jsonPath("$.http[0].waiting.min").value(5.0))
                .andExpect(jsonPath("$.http[0].waiting.max").value(14.0))
                // VUS aggregations
                .andExpect(jsonPath("$.vus.min").value(5))
                .andExpect(jsonPath("$.vus.max").value(15))
                .andExpect(jsonPath("$.vus.avg").value(10.0))
                // Host aggregations
                .andExpect(jsonPath("$.hosts.length()").value(1))
                .andExpect(jsonPath("$.hosts[0].hostId").value(host.getId().toString()))
                .andExpect(jsonPath("$.hosts[0].hostName").value(host.getName()))
                .andExpect(jsonPath("$.hosts[0].resource.cpu.avg").value(50.0))
                .andExpect(jsonPath("$.hosts[0].resource.cpu.max").value(60.0))
                .andExpect(jsonPath("$.hosts[0].resource.networkInTotalBytes").value(300))
                .andExpect(jsonPath("$.hosts[0].resource.networkOutTotalBytes").value(150))
                // Service aggregations
                .andExpect(jsonPath("$.hosts[0].services.length()").value(1))
                .andExpect(jsonPath("$.hosts[0].services[0].serviceId").value(service.getId().toString()))
                .andExpect(jsonPath("$.hosts[0].services[0].resource.cpu.avg").value(25.0))
                .andExpect(jsonPath("$.hosts[0].services[0].resource.cpu.max").value(30.0));
    }

    @Test
    void shouldReturnDynamicPercentiles() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");
        LocalDateTime baseTime = LocalDateTime.of(2025, 1, 15, 10, 0, 0);

        for (int i = 0; i < 100; i++) {
            metricK6HttpRepository.saveAndFlush(new MetricK6Http(
                    run, baseTime.plusSeconds(i), "http://test.com/api",
                    "GET", "group-a", 200, 200,
                    1000, 500, i + 1, i + 1));
        }

        mockMvc.perform(get(runUrl(environmentId, benchmarkId, run.getId().toString()))
                        .param("percentiles", "p99,p50")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.http[0].duration.percentiles.p99").isNumber())
                .andExpect(jsonPath("$.http[0].duration.percentiles.p50").isNumber())
                .andExpect(jsonPath("$.http[0].duration.percentiles.p90").doesNotExist())
                .andExpect(jsonPath("$.http[0].duration.percentiles.p95").doesNotExist());
    }

    @Test
    void shouldGroupByRequestGroup() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");
        LocalDateTime baseTime = LocalDateTime.of(2025, 1, 15, 10, 0, 0);

        metricK6HttpRepository.saveAndFlush(new MetricK6Http(
                run, baseTime, "http://test.com/api/a", "GET", "group-a",
                200, 200, 100, 50, 10, 5));
        metricK6HttpRepository.saveAndFlush(new MetricK6Http(
                run, baseTime.plusSeconds(1), "http://test.com/api/b", "POST", "group-b",
                201, 201, 200, 100, 20, 10));

        mockMvc.perform(get(runUrl(environmentId, benchmarkId, run.getId().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.http.length()").value(2))
                .andExpect(jsonPath("$.http[0].requestGroup").value("group-a"))
                .andExpect(jsonPath("$.http[0].totalRequests").value(1))
                .andExpect(jsonPath("$.http[1].requestGroup").value("group-b"))
                .andExpect(jsonPath("$.http[1].totalRequests").value(1));
    }

    @Test
    void shouldCalculateErrorRate() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");
        LocalDateTime baseTime = LocalDateTime.of(2025, 1, 15, 10, 0, 0);

        // 3 successful, 1 error
        metricK6HttpRepository.saveAndFlush(new MetricK6Http(
                run, baseTime, "http://test.com", "GET", "group-a", 200, 200, 100, 50, 10, 5));
        metricK6HttpRepository.saveAndFlush(new MetricK6Http(
                run, baseTime.plusSeconds(1), "http://test.com", "GET", "group-a", 200, 200, 100, 50, 10, 5));
        metricK6HttpRepository.saveAndFlush(new MetricK6Http(
                run, baseTime.plusSeconds(2), "http://test.com", "GET", "group-a", 200, 200, 100, 50, 10, 5));
        metricK6HttpRepository.saveAndFlush(new MetricK6Http(
                run, baseTime.plusSeconds(3), "http://test.com", "GET", "group-a", 500, 200, 100, 50, 10, 5));

        mockMvc.perform(get(runUrl(environmentId, benchmarkId, run.getId().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.http[0].errorRate").value(25.0));
    }

    @Test
    void shouldReturnEmptyDataForRunWithoutMetrics() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");

        mockMvc.perform(get(runUrl(environmentId, benchmarkId, run.getId().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.id").value(run.getId().toString()))
                .andExpect(jsonPath("$.http.length()").value(0))
                .andExpect(jsonPath("$.vus").doesNotExist())
                .andExpect(jsonPath("$.hosts.length()").value(0));
    }

    @Test
    void shouldReturn404ForNonExistentRun() throws Exception {
        mockMvc.perform(get(runUrl(environmentId, benchmarkId, UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldEnforceOwnership() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");

        mockMvc.perform(get(runUrl(environmentId, benchmarkId, run.getId().toString()))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");

        mockMvc.perform(get(runUrl(environmentId, benchmarkId, run.getId().toString())))
                .andExpect(status().isUnauthorized());
    }

    // --- Helpers ---

    private String runUrl(String envId, String bmId, String runId) {
        return "/v1/environments/" + envId + "/benchmarks/" + bmId + "/runs/" + runId;
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
        String machineId = "derived-machine-" + UUID.randomUUID();
        String serviceName = "derived-test-svc-" + UUID.randomUUID();
        String json = objectMapper.writeValueAsString(Map.of(
                "services", List.of(Map.of(
                        "serviceName", serviceName,
                        "imageName", "nginx:latest",
                        "replicas", List.of(Map.of(
                                "containerId", "container-" + UUID.randomUUID(),
                                "containerName", serviceName + "-1",
                                "machineId", machineId
                        ))
                )),
                "hosts", List.of(Map.of(
                        "name", "derived-test-host",
                        "machineId", machineId,
                        "os", "Linux",
                        "ipAddress", "192.168.1.200"
                ))
        ));
        mockMvc.perform(post("/v1/environments/" + envId + "/services")
                        .header("Authorization", "Bearer " + envToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    private BenchmarkRun createRunDirectly(UUID benchmarkUuid, String userEmail) {
        Benchmark benchmark = benchmarkRepository.findById(benchmarkUuid).orElseThrow();
        User user = userRepository.findByEmail(userEmail).orElseThrow();
        BenchmarkRun run = new BenchmarkRun();
        run.setBenchmark(benchmark);
        run.setStartedByUser(user);
        run.setStatus(BenchmarkRunStatus.PENDING_START);
        return benchmarkRunRepository.saveAndFlush(run);
    }
}
