package dk.viplev.api.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

class JwtIssuerTest {

    private JwtIssuer jwtIssuer;
    private static final String SECRET = "test-secret";

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey(SECRET);
        jwtIssuer = new JwtIssuer(properties);
    }

    @Test
    void shouldIssueTokenWithCorrectClaims() {
        UUID userId = UUID.randomUUID();
        String email = "test@viplev.dk";
        List<String> roles = List.of("USER", "ADMIN");

        String token = jwtIssuer.issue(userId, email, roles);

        DecodedJWT decoded = JWT.require(Algorithm.HMAC256(SECRET)).build().verify(token);
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaim("email").asString()).isEqualTo(email);
        assertThat(decoded.getClaim("roles").asList(String.class)).containsExactly("USER", "ADMIN");
    }

    @Test
    void shouldSetExpirationInFuture() {
        String token = jwtIssuer.issue(UUID.randomUUID(), "test@viplev.dk", List.of("USER"));

        DecodedJWT decoded = JWT.require(Algorithm.HMAC256(SECRET)).build().verify(token);
        assertThat(decoded.getExpiresAtAsInstant()).isAfter(Instant.now());
    }

    @Test
    void shouldSignWithHMAC256() {
        String token = jwtIssuer.issue(UUID.randomUUID(), "test@viplev.dk", List.of("USER"));

        DecodedJWT decoded = JWT.decode(token);
        assertThat(decoded.getAlgorithm()).isEqualTo("HS256");
    }
}
