package dk.viplev.api.port.inbound;

import dk.viplev.api.adapter.inbound.rest.dto.LoginDTO;

import java.util.UUID;

public interface AuthService {

    LoginDTO attemptLogin(String email, String password);

    UUID getAuthenticatedUserId();
}
