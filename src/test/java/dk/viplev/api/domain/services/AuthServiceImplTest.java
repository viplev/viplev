package dk.viplev.api.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import dk.viplev.api.config.security.JwtIssuer;
import dk.viplev.api.config.security.UserPrincipal;
import dk.viplev.api.config.security.UserPrincipalAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private JwtIssuer jwtIssuer;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void shouldReturnLoginDTOOnSuccessfulLogin() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .email("test@viplev.dk")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        var authToken = new UserPrincipalAuthenticationToken(principal);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authToken);
        when(jwtIssuer.issue(any(), any(), any())).thenReturn("jwt-token");

        var result = authService.attemptLogin("test@viplev.dk", "password");

        assertThat(result.getEmail()).isEqualTo("test@viplev.dk");
        assertThat(result.getToken()).isEqualTo("jwt-token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStripRolePrefixBeforeIssuingToken() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .email("test@viplev.dk")
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();
        var authToken = new UserPrincipalAuthenticationToken(principal);

        when(authenticationManager.authenticate(any())).thenReturn(authToken);
        when(jwtIssuer.issue(any(), any(), any())).thenReturn("jwt-token");

        authService.attemptLogin("test@viplev.dk", "password");

        ArgumentCaptor<List<String>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        verify(jwtIssuer).issue(eq(userId), eq("test@viplev.dk"), rolesCaptor.capture());
        assertThat(rolesCaptor.getValue()).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void shouldPropagateAuthenticationException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.attemptLogin("test@viplev.dk", "wrong"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
