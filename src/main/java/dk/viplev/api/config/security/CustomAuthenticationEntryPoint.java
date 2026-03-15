package dk.viplev.api.config.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dk.viplev.api.adapter.inbound.rest.dto.ErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorDTO error = new ErrorDTO()
            .status(401)
            .type(URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/401"))
            .title("Unauthorized")
            .detail("Authentication is required to access this resource")
            .instance(URI.create(request.getRequestURI()));

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
