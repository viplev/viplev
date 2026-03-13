package dk.viplev.api.adapter.inbound.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import dk.viplev.api.adapter.inbound.rest.dto.LoginRequestDTO;
import dk.viplev.api.adapter.inbound.rest.dto.LoginResponseDTO;
import dk.viplev.api.config.security.JwtIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthApiDelegateImpl implements AuthApiDelegate {
    
    private final JwtIssuer jwtIssuer;
    
    @Override
    public ResponseEntity<LoginResponseDTO> login(LoginRequestDTO loginRequestDTO) {
        
        log.info("Her er kommet et request");

        String token = jwtIssuer.issue(1L, "user1@viplev.dk", List.of("USER"));


        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);

        return ResponseEntity.ok(response);
        
        
    }
    
    
    

}
