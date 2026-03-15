package dk.viplev.api.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class UserPrincipalAuthenticationTokenTest {

    private UserPrincipal createPrincipal(List<SimpleGrantedAuthority> authorities) {
        return UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .email("test@viplev.dk")
                .password("hashed")
                .authorities(authorities)
                .build();
    }

    @Test
    void shouldBeAuthenticated() {
        var token = new UserPrincipalAuthenticationToken(createPrincipal(List.of()));

        assertThat(token.isAuthenticated()).isTrue();
    }

    @Test
    void shouldReturnPrincipal() {
        UserPrincipal principal = createPrincipal(List.of());
        var token = new UserPrincipalAuthenticationToken(principal);

        assertThat(token.getPrincipal()).isSameAs(principal);
    }

    @Test
    void shouldReturnNullCredentials() {
        var token = new UserPrincipalAuthenticationToken(createPrincipal(List.of()));

        assertThat(token.getCredentials()).isNull();
    }

    @Test
    void shouldDelegateAuthoritiesFromPrincipal() {
        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN"));
        var token = new UserPrincipalAuthenticationToken(createPrincipal(authorities));

        assertThat(token.getAuthorities()).containsExactlyInAnyOrderElementsOf(authorities);
    }
}
