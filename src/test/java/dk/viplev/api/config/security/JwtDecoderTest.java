package dk.viplev.api.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

class JwtDecoderTest {

    private JwtDecoder jwtDecoder;
    private JwtIssuer jwtIssuer;
    private static final String SECRET = "test-secret";

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey(SECRET);
        jwtDecoder = new JwtDecoder(properties);
        jwtIssuer = new JwtIssuer(properties);
    }

    @Test
    void shouldDecodeValidToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtIssuer.issue(userId, "test@viplev.dk", List.of("USER"));

        DecodedJWT decoded = jwtDecoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaim("email").asString()).isEqualTo("test@viplev.dk");
        assertThat(decoded.getClaim("roles").asList(String.class)).containsExactly("USER");
    }

    @Test
    void shouldThrowOnInvalidSignature() {
        JwtProperties wrongProperties = new JwtProperties();
        wrongProperties.setSecretKey("wrong-secret");
        JwtIssuer wrongIssuer = new JwtIssuer(wrongProperties);

        String token = wrongIssuer.issue(UUID.randomUUID(), "test@viplev.dk", List.of("USER"));

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void shouldThrowOnExpiredToken() {
        String expiredToken = JWT.create()
                .withSubject(UUID.randomUUID().toString())
                .withExpiresAt(Instant.now().minusSeconds(60))
                .sign(Algorithm.HMAC256(SECRET));

        assertThatThrownBy(() -> jwtDecoder.decode(expiredToken))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void shouldThrowOnMalformedToken() {
        assertThatThrownBy(() -> jwtDecoder.decode("not-a-jwt"))
                .isInstanceOf(JWTVerificationException.class);
    }
}
