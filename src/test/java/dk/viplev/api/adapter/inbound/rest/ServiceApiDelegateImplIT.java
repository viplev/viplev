package dk.viplev.api.adapter.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class ServiceApiDelegateImplIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String user1Token;
    private String user2Token;

    @BeforeEach
    void setUp() throws Exception {
        user1Token = loginAndGetToken("user1@viplev.dk", "password");
        user2Token = loginAndGetToken("user2@viplev.dk", "password");
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

    private String buildRegistrationJson(String hostName, List<Map<String, Object>> services) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "hostName", hostName,
                "services", services));
    }

    @Test
    void shouldListServicesForEnvironment() throws Exception {
        JsonNode env = createEnvironmentAndGetResponse("Service List Env", user1Token);
        String envId = env.get("id").asText();
        String envToken = env.get("token").asText();

        // Register services via agent (host auto-created)
        String registrationJson = buildRegistrationJson("test-host", List.of(
                Map.of("serviceName", "svc-list-1", "imageName", "nginx:latest"),
                Map.of("serviceName", "svc-list-2", "imageName", "redis:7")));

        mockMvc.perform(post("/v1/environments/" + envId + "/services")
                        .header("Authorization", "Bearer " + envToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson))
                .andExpect(status().isCreated());

        // List services as user
        mockMvc.perform(get("/v1/environments/" + envId + "/services")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.serviceName == 'svc-list-1')]").exists())
                .andExpect(jsonPath("$[?(@.serviceName == 'svc-list-2')]").exists());
    }

    @Test
    void shouldGetSingleService() throws Exception {
        JsonNode env = createEnvironmentAndGetResponse("Service Get Env", user1Token);
        String envId = env.get("id").asText();
        String envToken = env.get("token").asText();

        String registrationJson = buildRegistrationJson("test-host", List.of(
                Map.of("serviceName", "svc-get-single", "imageName", "nginx:latest")));

        mockMvc.perform(post("/v1/environments/" + envId + "/services")
                        .header("Authorization", "Bearer " + envToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson))
                .andExpect(status().isCreated());

        // Get the service ID from the list
        MvcResult listResult = mockMvc.perform(get("/v1/environments/" + envId + "/services")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode services = objectMapper.readTree(listResult.getResponse().getContentAsString());
        String serviceId = services.get(0).get("id").asText();

        mockMvc.perform(get("/v1/environments/" + envId + "/services/" + serviceId)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("svc-get-single"))
                .andExpect(jsonPath("$.imageName").value("nginx:latest"));
    }

    @Test
    void shouldReturn404WhenAccessingOtherUsersServices() throws Exception {
        JsonNode env = createEnvironmentAndGetResponse("Private Service Env", user1Token);
        String envId = env.get("id").asText();

        // user2 should get 404
        mockMvc.perform(get("/v1/environments/" + envId + "/services")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSyncServicesOnRegister() throws Exception {
        JsonNode env = createEnvironmentAndGetResponse("Sync Env", user1Token);
        String envId = env.get("id").asText();
        String envToken = env.get("token").asText();

        // First registration: 2 services
        String firstBatch = buildRegistrationJson("test-host", List.of(
                Map.of("serviceName", "svc-sync-a", "imageName", "nginx:1.0"),
                Map.of("serviceName", "svc-sync-b", "imageName", "redis:6")));

        mockMvc.perform(post("/v1/environments/" + envId + "/services")
                        .header("Authorization", "Bearer " + envToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBatch))
                .andExpect(status().isCreated());

        // Second registration: update one, add one, remove one
        String secondBatch = buildRegistrationJson("test-host", List.of(
                Map.of("serviceName", "svc-sync-a", "imageName", "nginx:2.0"),
                Map.of("serviceName", "svc-sync-c", "imageName", "postgres:15")));

        mockMvc.perform(post("/v1/environments/" + envId + "/services")
                        .header("Authorization", "Bearer " + envToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBatch))
                .andExpect(status().isCreated());

        // Verify: svc-sync-a updated, svc-sync-b deleted, svc-sync-c added
        mockMvc.perform(get("/v1/environments/" + envId + "/services")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.serviceName == 'svc-sync-a')].imageName").value("nginx:2.0"))
                .andExpect(jsonPath("$[?(@.serviceName == 'svc-sync-c')]").exists())
                .andExpect(jsonPath("$[?(@.serviceName == 'svc-sync-b')]").doesNotExist());
    }

    @Test
    void shouldReturn404ForNonExistentService() throws Exception {
        JsonNode env = createEnvironmentAndGetResponse("404 Service Env", user1Token);
        String envId = env.get("id").asText();

        mockMvc.perform(get("/v1/environments/" + envId + "/services/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }
}
