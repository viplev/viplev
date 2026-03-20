package dk.viplev.api.domain.services;


import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import dk.viplev.api.adapter.inbound.rest.dto.LoginDTO;
import dk.viplev.api.config.security.JwtIssuer;
import dk.viplev.api.config.security.UserPrincipal;
import dk.viplev.api.port.inbound.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final JwtIssuer jwtIssuer;
    private final AuthenticationManager authenticationManager;

    @Override
    public LoginDTO attemptLogin(String email, String password) {
        var authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, password)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        var roles = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(authority -> authority.replace("ROLE_", ""))
            .toList();

        String token = jwtIssuer.issue(principal.getUserId(), principal.getEmail(), roles);

        LoginDTO response = new LoginDTO();
        response.setEmail(principal.getEmail());
        response.setToken(token);

        return response;
    }

    @Override
    public UUID getAuthenticatedUserId() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getUserId();
    }

    @Override
    public UUID getAuthenticatedEnvironmentId() {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getEnvironmentId();
    }
}
