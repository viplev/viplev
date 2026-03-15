package dk.viplev.api.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;

import dk.viplev.api.adapter.inbound.rest.dto.LoginDTO;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiDelegateImplIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginJson(String email, String password) throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setEmail(email);
        dto.setPassword(password);
        return objectMapper.writeValueAsString(dto);
    }

    @Test
    void shouldLoginWithValidCredentials() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin1@viplev.dk", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin1@viplev.dk"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void shouldLoginAndReceiveValidJwt() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("user1@viplev.dk", "password")))
                .andExpect(status().isOk())
                .andReturn();

        LoginDTO response = objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginDTO.class);

        DecodedJWT decoded = JWT.decode(response.getToken());
        assertThat(decoded.getClaim("email").asString()).isEqualTo("user1@viplev.dk");
        assertThat(decoded.getClaim("roles").asList(String.class)).containsExactly("USER");
    }

    @Test
    void shouldReturn401ForInvalidPassword() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin1@viplev.dk", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401ForNonExistentUser() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nonexistent@viplev.dk", "password")))
                .andExpect(status().isUnauthorized());
    }
}
