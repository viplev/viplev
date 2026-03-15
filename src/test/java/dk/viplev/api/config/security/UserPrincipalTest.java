package dk.viplev.api.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class UserPrincipalTest {

    @Test
    void shouldBuildWithAllFields() {
        UUID userId = UUID.randomUUID();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .email("test@viplev.dk")
                .password("hashed")
                .authorities(authorities)
                .build();

        assertThat(principal.getUserId()).isEqualTo(userId);
        assertThat(principal.getEmail()).isEqualTo("test@viplev.dk");
        assertThat(principal.getPassword()).isEqualTo("hashed");
        assertThat(principal.getAuthorities()).isEqualTo(authorities);
    }

    @Test
    void shouldReturnEmailAsUsername() {
        UserPrincipal principal = UserPrincipal.builder()
                .email("test@viplev.dk")
                .build();

        assertThat(principal.getUsername()).isEqualTo("test@viplev.dk");
    }

    @Test
    void shouldReturnTrueForAllAccountStatusMethods() {
        UserPrincipal principal = UserPrincipal.builder().build();

        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }
}
