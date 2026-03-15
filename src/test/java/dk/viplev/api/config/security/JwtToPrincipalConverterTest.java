package dk.viplev.api.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

class JwtToPrincipalConverterTest {

    private JwtToPrincipalConverter converter;
    private JwtIssuer jwtIssuer;
    private JwtDecoder jwtDecoder;
    private static final String SECRET = "test-secret";

    @BeforeEach
    void setUp() {
        converter = new JwtToPrincipalConverter();
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey(SECRET);
        jwtIssuer = new JwtIssuer(properties);
        jwtDecoder = new JwtDecoder(properties);
    }

    @Test
    void shouldConvertToUserPrincipalWithCorrectFields() {
        UUID userId = UUID.randomUUID();
        String token = jwtIssuer.issue(userId, "test@viplev.dk", List.of("USER"));
        DecodedJWT decoded = jwtDecoder.decode(token);

        UserPrincipal principal = converter.convert(decoded);

        assertThat(principal.getUserId()).isEqualTo(userId);
        assertThat(principal.getEmail()).isEqualTo("test@viplev.dk");
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void shouldAddRolePrefixToAuthorities() {
        UUID userId = UUID.randomUUID();
        String token = jwtIssuer.issue(userId, "test@viplev.dk", List.of("USER", "ADMIN"));
        DecodedJWT decoded = jwtDecoder.decode(token);

        UserPrincipal principal = converter.convert(decoded);

        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void shouldReturnEmptyAuthoritiesWhenRolesClaimMissing() {
        String token = JWT.create()
                .withSubject(UUID.randomUUID().toString())
                .withClaim("email", "test@viplev.dk")
                .withExpiresAt(Instant.now().plusSeconds(60))
                .sign(Algorithm.HMAC256(SECRET));
        DecodedJWT decoded = jwtDecoder.decode(token);

        UserPrincipal principal = converter.convert(decoded);

        assertThat(principal.getAuthorities()).isEmpty();
    }
}
