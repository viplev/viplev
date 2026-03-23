package dk.viplev.api.adapter.inbound.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest
@AutoConfigureMockMvc
class EnvironmentApiDelegateImplIT {

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
                java.util.Map.of("email", email, "password", password));

        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private String environmentJson(String name, String type) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("name", name, "type", type));
    }

    private String environmentJsonWithDescription(String name, String type, String description) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("name", name, "type", type, "description", description));
    }

    @Test
    void shouldCreateEnvironment() throws Exception {
        mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(environmentJson("My Docker Env", "docker")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My Docker Env"))
                .andExpect(jsonPath("$.type").value("docker"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.agentCommand").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.agentLastSeenAt").doesNotExist());
    }

    @Test
    void shouldListOnlyOwnEnvironments() throws Exception {
        // Create environment as user1
        mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(environmentJson("User1 Env", "docker")))
                .andExpect(status().isCreated());

        // Create environment as user2
        mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(environmentJson("User2 Env", "kubernetes")))
                .andExpect(status().isCreated());

        // User2 should only see their own
        mockMvc.perform(get("/v1/environments")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'User2 Env')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'User1 Env')]").doesNotExist());
    }

    @Test
    void shouldGetEnvironmentById() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(environmentJson("Get Test Env", "docker")))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/v1/environments/" + id)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Get Test Env"))
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void shouldReturn404WhenGettingOtherUsersEnvironment() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(environmentJson("Private Env", "docker")))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // User2 should get 404
        mockMvc.perform(get("/v1/environments/" + id)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateEnvironment() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(environmentJson("Original Name", "docker")))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(put("/v1/environments/" + id)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(environmentJsonWithDescription("Updated Name", "kubernetes", "New description")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.type").value("kubernetes"))
                .andExpect(jsonPath("$.description").value("New description"));
    }

    @Test
    void shouldDeleteEnvironment() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(environmentJson("To Delete", "docker")))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(delete("/v1/environments/" + id)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/v1/environments/" + id)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForNonExistentEnvironment() throws Exception {
        mockMvc.perform(get("/v1/environments/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/v1/environments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldGenerateDockerAgentCommand() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/environments")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(environmentJson("Docker Env", "docker")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        String agentCommand = node.get("agentCommand").asText();
        String token = node.get("token").asText();

        org.assertj.core.api.Assertions.assertThat(agentCommand)
                .isEqualTo("docker run -e VIPLEV_TOKEN=" + token + " ghcr.io/viplev/agent:latest");
    }
}
