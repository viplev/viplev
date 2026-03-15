package dk.viplev.api.config.security;

import java.util.List;
import java.util.Set;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * This class is not implemented from an interface in the code, but from an interface within springframework
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return UserPrincipal.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .password(user.getPassword())
            .authorities(mapRolesToAuthorities(user.getRoles()))
            
            .build();

    }

    private List<SimpleGrantedAuthority> mapRolesToAuthorities(Set<String> roles) {
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
    }

}
