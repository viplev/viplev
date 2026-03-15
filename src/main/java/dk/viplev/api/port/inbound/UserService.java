package dk.viplev.api.port.inbound;

import java.util.Optional;

import dk.viplev.api.adapter.inbound.rest.dto.LoginDTO;
import dk.viplev.api.domain.model.User;

public interface UserService {
    
    Optional<User> login(LoginDTO login);

}
