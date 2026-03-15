package dk.viplev.api.config.security;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtIssuer {

    private final JwtProperties properties;

    public String issue(UUID userId, String email, List<String> roles) {
        return JWT.create()
            .withSubject(userId.toString())
            .withExpiresAt(Instant.now().plus(Duration.of(15, ChronoUnit.SECONDS)))
            .withClaim("email", email)
            .withClaim("roles", roles)
            .sign(Algorithm.HMAC256(properties.getSecretKey()));
    }
}
