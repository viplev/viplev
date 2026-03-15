package dk.viplev.api.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    private User createUser(String email, Set<String> roles) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setRoles(roles);
        return user;
    }

    @Test
    void shouldReturnUserPrincipalWhenUserExists() {
        User user = createUser("test@viplev.dk", Set.of("USER"));
        when(userRepository.findByEmail("test@viplev.dk")).thenReturn(Optional.of(user));

        var result = (UserPrincipal) service.loadUserByUsername("test@viplev.dk");

        assertThat(result.getUserId()).isEqualTo(user.getId());
        assertThat(result.getEmail()).isEqualTo("test@viplev.dk");
        assertThat(result.getPassword()).isEqualTo("hashed-password");
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void shouldThrowUsernameNotFoundExceptionWhenUserNotFound() {
        when(userRepository.findByEmail("unknown@viplev.dk")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("unknown@viplev.dk"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shouldMapRolesWithRolePrefix() {
        User user = createUser("admin@viplev.dk", Set.of("ADMIN", "USER"));
        when(userRepository.findByEmail("admin@viplev.dk")).thenReturn(Optional.of(user));

        var result = (UserPrincipal) service.loadUserByUsername("admin@viplev.dk");

        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }
}
