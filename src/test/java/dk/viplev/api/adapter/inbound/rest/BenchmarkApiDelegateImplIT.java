package dk.viplev.api.adapter.inbound.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.BenchmarkServiceRepository;
import dk.viplev.api.port.outbound.db.ServiceRepository;
import dk.viplev.api.util.TestObjectFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BenchmarkApiDelegateImplIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BenchmarkRunRepository benchmarkRunRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private BenchmarkServiceRepository benchmarkServiceRepository;

    @Autowired
    private TestObjectFactory testObjectFactory;

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
                Map.of("email", email, "password", password));

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
                Map.of("name", name, "type", type));

        MvcResult result = mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private String getEnvironmentToken(String userToken, String environmentId) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/environments/" + environmentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String benchmarkUrl(String environmentId) {
        return "/v1/environments/" + environmentId + "/benchmarks";
    }

    private String benchmarkUrl(String environmentId, String benchmarkId) {
        return "/v1/environments/" + environmentId + "/benchmarks/" + benchmarkId;
    }

    private String benchmarkJson(String name, String description, String k6Instructions) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("name", name, "description", description, "k6Instructions", k6Instructions));
    }

    private String registerServicesPayload(String machineId, String... serviceNames) throws Exception {
        List<Map<String, Object>> services = new ArrayList<>();
        for (String serviceName : serviceNames) {
            services.add(Map.of(
                    "serviceName", serviceName,
                    "imageName", "nginx:latest",
                    "replicas", List.of(Map.of(
                            "containerId", "container-" + UUID.randomUUID(),
                            "containerName", serviceName + "-1",
                            "machineId", machineId
                    ))
            ));
        }

        return objectMapper.writeValueAsString(Map.of(
                "services", services,
                "hosts", List.of(Map.of(
                        "name", "benchmark-scope-host",
                        "machineId", machineId,
                        "os", "Linux",
                        "ipAddress", "192.168.1.250"
                ))
        ));
    }

    @Test
    void shouldPerformFullCrudFlow() throws Exception {
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

        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Load Test')]").exists());

        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Load Test"))
                .andExpect(jsonPath("$.id").value(benchmarkId));

        mockMvc.perform(put(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Updated Test", "Updated desc", "export default function() {}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Test"))
                .andExpect(jsonPath("$.description").value("Updated desc"))
                .andExpect(jsonPath("$.k6Instructions").value("export default function() {}"));

        mockMvc.perform(delete(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldEnforceOwnership() throws Exception {
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Private Benchmark", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Hacked", "hacked", "hacked")))
                .andExpect(status().isNotFound());

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
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Env1 Benchmark", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        String env2Id = createEnvironment(user1Token, "Second Env", "docker");

        mockMvc.perform(get(benchmarkUrl(env2Id, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId)))
                .andExpect(status().isUnauthorized());
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

        testObjectFactory.createBenchmarkRunDirectly(UUID.fromString(bmId), "user1@viplev.dk");

        mockMvc.perform(delete(benchmarkUrl(user1EnvironmentId, bmId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId, bmId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());

        assertThat(benchmarkRunRepository.findByBenchmarkId(UUID.fromString(bmId))).isEmpty();
    }

    @Test
    void shouldPatchBenchmarkServicesScopeAndExposeServiceIdsOnGet() throws Exception {
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Scoped Benchmark", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        String machineId = "scope-machine-" + UUID.randomUUID();
        mockMvc.perform(post("/v1/environments/" + user1EnvironmentId + "/services")
                        .header("Authorization", "Bearer " + getEnvironmentToken(user1Token, user1EnvironmentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerServicesPayload(machineId, "svc-a-" + UUID.randomUUID(), "svc-b-" + UUID.randomUUID())))
                .andExpect(status().isCreated());

        var services = serviceRepository.findByEnvironmentIdAndDeletedAtIsNull(UUID.fromString(user1EnvironmentId));
        UUID serviceA = services.get(0).getId();
        UUID serviceB = services.get(1).getId();

        String patchBody = objectMapper.writeValueAsString(Map.of("serviceIds", List.of(serviceA, serviceB)));

        mockMvc.perform(patch(benchmarkUrl(user1EnvironmentId, benchmarkId) + "/services")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(benchmarkUrl(user1EnvironmentId, benchmarkId))
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceIds.length()").value(2));
    }

    @Test
    void shouldReturn409WhenPatchingServicesWithActiveRun() throws Exception {
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Conflict Benchmark", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        testObjectFactory.createBenchmarkRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        String patchBody = objectMapper.writeValueAsString(Map.of("serviceIds", List.of()));
        mockMvc.perform(patch(benchmarkUrl(user1EnvironmentId, benchmarkId) + "/services")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400ForInvalidOrDuplicateServiceIdsWhenPatchingServices() throws Exception {
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Invalid Scope Benchmark", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        UUID randomServiceId = UUID.randomUUID();
        String invalidBody = objectMapper.writeValueAsString(Map.of("serviceIds", List.of(randomServiceId)));
        mockMvc.perform(patch(benchmarkUrl(user1EnvironmentId, benchmarkId) + "/services")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());

        String duplicateBody = objectMapper.writeValueAsString(Map.of("serviceIds", List.of(randomServiceId, randomServiceId)));
        mockMvc.perform(patch(benchmarkUrl(user1EnvironmentId, benchmarkId) + "/services")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409EvenWhenServiceIdsAreInvalidIfRunIsActive() throws Exception {
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Conflict Priority Benchmark", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        testObjectFactory.createBenchmarkRunDirectly(UUID.fromString(benchmarkId), "user1@viplev.dk", BenchmarkRunStatus.STARTED);

        String invalidBody = objectMapper.writeValueAsString(Map.of("serviceIds", List.of(UUID.randomUUID())));
        mockMvc.perform(patch(benchmarkUrl(user1EnvironmentId, benchmarkId) + "/services")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400WhenPatchRequestBodyIsMissing() throws Exception {
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Missing ServiceIds Benchmark", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch(benchmarkUrl(user1EnvironmentId, benchmarkId) + "/services")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCreateNewRowWhenReAddingPreviouslyRemovedService() throws Exception {
        MvcResult createResult = mockMvc.perform(post(benchmarkUrl(user1EnvironmentId))
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(benchmarkJson("Readd Benchmark", "desc", "script")))
                .andExpect(status().isCreated())
                .andReturn();

        String benchmarkId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        String machineId = "readd-machine-" + UUID.randomUUID();
        mockMvc.perform(post("/v1/environments/" + user1EnvironmentId + "/services")
                        .header("Authorization", "Bearer " + getEnvironmentToken(user1Token, user1EnvironmentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerServicesPayload(machineId, "svc-readd-" + UUID.randomUUID())))
                .andExpect(status().isCreated());

        UUID serviceId = serviceRepository.findByEnvironmentIdAndDeletedAtIsNull(UUID.fromString(user1EnvironmentId)).getFirst().getId();

        String addBody = objectMapper.writeValueAsString(Map.of("serviceIds", List.of(serviceId)));
        String removeBody = objectMapper.writeValueAsString(Map.of("serviceIds", List.of()));

        mockMvc.perform(patch(benchmarkUrl(user1EnvironmentId, benchmarkId) + "/services")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch(benchmarkUrl(user1EnvironmentId, benchmarkId) + "/services")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(removeBody))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch(benchmarkUrl(user1EnvironmentId, benchmarkId) + "/services")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody))
                .andExpect(status().isNoContent());

        assertThat(benchmarkServiceRepository.findAll().stream()
                .filter(bs -> bs.getBenchmark().getId().equals(UUID.fromString(benchmarkId)))
                .filter(bs -> bs.getService().getId().equals(serviceId)))
                .hasSize(2);
    }
}
