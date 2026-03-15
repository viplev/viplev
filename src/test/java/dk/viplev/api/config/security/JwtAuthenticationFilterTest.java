package dk.viplev.api.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private JwtToPrincipalConverter jwtToPrincipalConverter;

    @Mock
    private DecodedJWT decodedJWT;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateWithValidBearerToken() throws Exception {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .email("test@viplev.dk")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtDecoder.decode("valid-token")).thenReturn(decodedJWT);
        when(jwtToPrincipalConverter.convert(decodedJWT)).thenReturn(principal);

        filter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(UserPrincipalAuthenticationToken.class);
        assertThat(auth.getPrincipal()).isSameAs(principal);
    }

    @Test
    void shouldContinueChainWhenNoAuthorizationHeader() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    void shouldContinueChainWhenHeaderIsNotBearer() throws Exception {
        request.addHeader("Authorization", "Basic xyz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    void shouldClearContextOnDecodeException() throws Exception {
        request.addHeader("Authorization", "Bearer bad-token");
        when(jwtDecoder.decode("bad-token")).thenThrow(new JWTVerificationException("invalid"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    void shouldClearContextOnConverterException() throws Exception {
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtDecoder.decode("valid-token")).thenReturn(decodedJWT);
        when(jwtToPrincipalConverter.convert(any())).thenThrow(new RuntimeException("converter error"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isNotNull();
    }
}
