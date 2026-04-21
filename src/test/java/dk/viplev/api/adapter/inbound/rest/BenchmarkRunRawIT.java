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
import dk.viplev.api.domain.model.Service;
import dk.viplev.api.domain.model.ServiceReplica;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.HostRepository;
import dk.viplev.api.port.outbound.db.MetricK6HttpRepository;
import dk.viplev.api.port.outbound.db.MetricK6VusRepository;
import dk.viplev.api.port.outbound.db.MetricResourceHostRepository;
import dk.viplev.api.port.outbound.db.MetricResourceReplicaRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import dk.viplev.api.port.outbound.db.ServiceReplicaRepository;
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
class BenchmarkRunRawIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BenchmarkRunRepository benchmarkRunRepository;
    @Autowired private BenchmarkRepository benchmarkRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private HostRepository hostRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ServiceReplicaRepository serviceReplicaRepository;
    @Autowired private MetricK6HttpRepository metricK6HttpRepository;
    @Autowired private MetricK6VusRepository metricK6VusRepository;
    @Autowired private MetricResourceHostRepository metricResourceHostRepository;
    @Autowired private MetricResourceReplicaRepository metricResourceReplicaRepository;

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

        JsonNode env = createEnvironmentAndGetResponse("Raw Test Env", user1Token);
        environmentId = env.get("id").asText();
        String environmentToken = env.get("token").asText();

        benchmarkId = createBenchmark(user1Token, environmentId, "Raw Benchmark");

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
    void shouldReturnRawTimeSeriesWithHostServiceAndK6Data() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");
        LocalDateTime baseTime = LocalDateTime.of(2025, 1, 15, 10, 0, 0);

        // Seed host metrics
        metricResourceHostRepository.saveAndFlush(new MetricResourceHost(
                run, host, baseTime, 40.0, 1000.0, 2000.0, 100.0, 50.0, 20.0, 10.0));
        metricResourceHostRepository.saveAndFlush(new MetricResourceHost(
                run, host, baseTime.plusSeconds(5), 60.0, 1500.0, 2000.0, 200.0, 100.0, 30.0, 15.0));

        // Seed replica metrics
        metricResourceReplicaRepository.saveAndFlush(new MetricResourceReplica(
                run, replica, baseTime, 20.0, 500.0, 1000.0, 50.0, 25.0, 10.0, 5.0));
        metricResourceReplicaRepository.saveAndFlush(new MetricResourceReplica(
                run, replica, baseTime.plusSeconds(5), 30.0, 700.0, 1000.0, 80.0, 40.0, 15.0, 8.0));

        // Seed K6 HTTP metrics
        metricK6HttpRepository.saveAndFlush(new MetricK6Http(
                run, baseTime, "http://test.com/api", "GET", "group-a", 200, 200, 1000, 500, 150, 80));
        metricK6HttpRepository.saveAndFlush(new MetricK6Http(
                run, baseTime.plusSeconds(2), "http://test.com/api", "GET", "group-a", 200, 200, 1000, 500, 200, 120));

        // Seed K6 VUS metrics
        metricK6VusRepository.saveAndFlush(new MetricK6Vus(run, baseTime.plusSeconds(1), 10));
        metricK6VusRepository.saveAndFlush(new MetricK6Vus(run, baseTime.plusSeconds(3), 20));

        mockMvc.perform(get(rawUrl(environmentId, benchmarkId, run.getId().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                // Host data
                .andExpect(jsonPath("$.timeSeries.hosts.length()").value(1))
                .andExpect(jsonPath("$.timeSeries.hosts[0].hostId").value(host.getId().toString()))
                .andExpect(jsonPath("$.timeSeries.hosts[0].hostName").value(host.getName()))
                .andExpect(jsonPath("$.timeSeries.hosts[0].dataPoints.length()").value(2))
                .andExpect(jsonPath("$.timeSeries.hosts[0].dataPoints[0].cpuPercentage").value(40.0))
                .andExpect(jsonPath("$.timeSeries.hosts[0].dataPoints[0].memoryUsageBytes").value(1000.0))
                .andExpect(jsonPath("$.timeSeries.hosts[0].dataPoints[0].memoryLimitBytes").value(2000.0))
                .andExpect(jsonPath("$.timeSeries.hosts[0].dataPoints[0].networkInBytes").value(100.0))
                .andExpect(jsonPath("$.timeSeries.hosts[0].dataPoints[0].networkOutBytes").value(50.0))
                .andExpect(jsonPath("$.timeSeries.hosts[0].dataPoints[0].blockInBytes").value(20.0))
                .andExpect(jsonPath("$.timeSeries.hosts[0].dataPoints[0].blockOutBytes").value(10.0))
                .andExpect(jsonPath("$.timeSeries.hosts[0].dataPoints[1].cpuPercentage").value(60.0))
                // Service data nested under host
                .andExpect(jsonPath("$.timeSeries.hosts[0].services.length()").value(1))
                .andExpect(jsonPath("$.timeSeries.hosts[0].services[0].serviceId").value(service.getId().toString()))
                .andExpect(jsonPath("$.timeSeries.hosts[0].services[0].serviceName").value(service.getServiceName()))
                // Replica data nested under service
                .andExpect(jsonPath("$.timeSeries.hosts[0].services[0].replicas.length()").value(1))
                .andExpect(jsonPath("$.timeSeries.hosts[0].services[0].replicas[0].replicaId").value(replica.getId().toString()))
                .andExpect(jsonPath("$.timeSeries.hosts[0].services[0].replicas[0].containerId").value(replica.getContainerId()))
                .andExpect(jsonPath("$.timeSeries.hosts[0].services[0].replicas[0].dataPoints.length()").value(2))
                .andExpect(jsonPath("$.timeSeries.hosts[0].services[0].replicas[0].dataPoints[0].cpuPercentage").value(20.0))
                .andExpect(jsonPath("$.timeSeries.hosts[0].services[0].replicas[0].dataPoints[1].cpuPercentage").value(30.0))
                // K6 data (HTTP + VUS interleaved chronologically)
                .andExpect(jsonPath("$.timeSeries.k6.dataPoints.length()").value(4))
                .andExpect(jsonPath("$.timeSeries.k6.dataPoints[0].httpResponseTimeMs").value(150))
                .andExpect(jsonPath("$.timeSeries.k6.dataPoints[0].httpWaitingMs").value(80))
                .andExpect(jsonPath("$.timeSeries.k6.dataPoints[0].vus").doesNotExist())
                .andExpect(jsonPath("$.timeSeries.k6.dataPoints[1].vus").value(10))
                .andExpect(jsonPath("$.timeSeries.k6.dataPoints[1].httpResponseTimeMs").doesNotExist())
                .andExpect(jsonPath("$.timeSeries.k6.dataPoints[2].httpResponseTimeMs").value(200))
                .andExpect(jsonPath("$.timeSeries.k6.dataPoints[3].vus").value(20));
    }

    @Test
    void shouldReturnEmptyListsForRunWithoutMetrics() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");

        mockMvc.perform(get(rawUrl(environmentId, benchmarkId, run.getId().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSeries.hosts.length()").value(0))
                .andExpect(jsonPath("$.timeSeries.k6").doesNotExist());
    }

    @Test
    void shouldReturn404ForNonExistentRun() throws Exception {
        mockMvc.perform(get(rawUrl(environmentId, benchmarkId, UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldEnforceOwnership() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");

        mockMvc.perform(get(rawUrl(environmentId, benchmarkId, run.getId().toString()))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        BenchmarkRun run = createRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk");

        mockMvc.perform(get(rawUrl(environmentId, benchmarkId, run.getId().toString())))
                .andExpect(status().isUnauthorized());
    }

    // --- Helpers ---

    private String rawUrl(String envId, String bmId, String runId) {
        return "/v1/environments/" + envId + "/benchmarks/" + bmId + "/runs/" + runId + "/raw";
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
        String machineId = "raw-machine-" + UUID.randomUUID();
        String serviceName = "raw-test-svc-" + UUID.randomUUID();
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
                        "name", "raw-test-host",
                        "machineId", machineId,
                        "os", "Linux",
                        "ipAddress", "192.168.1.201"
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
