package dk.viplev.api.config.security;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtIssuer {


    // TODO: Secret skal ikke læses fra Properties. Den kan læses direkte fra en ENV fil. Dette skal rettes senere efter tutorial er done
    private final JwtProperties properties;

    // TODO: userId er som long, skal nok skrives om til en UUID på et senere tidspunkt.
    public String issue(long userId, String email, List<String> roles) {

        return JWT.create()
            .withSubject(String.valueOf(userId))
            .withExpiresAt(Instant.now().plus(Duration.of(1, ChronoUnit.DAYS)))
            .withClaim("email", email)
            .withClaim("roles", roles)            
            .sign(Algorithm.HMAC256(properties.getSecretKey()));


        // 
    }
    
}
