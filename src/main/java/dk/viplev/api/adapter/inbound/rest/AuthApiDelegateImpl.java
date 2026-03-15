package dk.viplev.api.adapter.inbound.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import dk.viplev.api.adapter.inbound.rest.dto.LoginDTO;
import dk.viplev.api.port.inbound.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthApiDelegateImpl implements AuthApiDelegate {

    
    private final AuthService authService;

    @Override
    public ResponseEntity<LoginDTO> login(LoginDTO request) {
        LoginDTO response = authService.attemptLogin(request.getEmail(), request.getPassword());

        return ResponseEntity.ok(response);
    }
}
