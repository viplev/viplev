package dk.viplev.api.config.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dk.viplev.api.adapter.inbound.rest.dto.ErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorDTO error = new ErrorDTO()
            .status(403)
            .type(URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/403"))
            .title("Forbidden")
            .detail("You do not have permission to access this resource")
            .instance(URI.create(request.getRequestURI()));

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
