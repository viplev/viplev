package dk.viplev.api.domain.services;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import dk.viplev.api.adapter.inbound.rest.dto.LoginDTO;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.inbound.UserService;
import dk.viplev.api.port.outbound.db.UserRepository;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Optional<User> login(LoginDTO login) {
        return userRepository.findByEmail(login.getEmail())
            .filter(user -> passwordEncoder.matches(login.getPassword(), user.getPassword()));
    }

}
