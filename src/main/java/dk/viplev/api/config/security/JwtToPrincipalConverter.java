package dk.viplev.api.config.security;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.auth0.jwt.interfaces.DecodedJWT;

@Component
public class JwtToPrincipalConverter {

    public UserPrincipal convert(DecodedJWT jwt) {
        return UserPrincipal.builder()
            .userId(UUID.fromString(jwt.getSubject()))
            .email(jwt.getClaim("email").asString())
            .authorities(extractAuthoritiesFromClaim(jwt))
            .build();
    }

    private List<SimpleGrantedAuthority> extractAuthoritiesFromClaim(DecodedJWT jwt) {
        var claim = jwt.getClaim("roles");

        if (claim.isNull() || claim.isMissing()) return List.of();

        return claim.asList(String.class).stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();

    }

    
}
