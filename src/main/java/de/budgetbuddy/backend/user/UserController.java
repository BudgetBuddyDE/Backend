package de.budgetbuddy.backend.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.auth.AuthorizationInterceptor;
import de.budgetbuddy.backend.user.avatar.UserAvatar;
import de.budgetbuddy.backend.user.avatar.UserAvatarRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/user")
public class UserController {
    private final UserRepository userRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    public UserController(UserRepository userRepository, UserAvatarRepository userAvatarRepository) {
        this.userRepository = userRepository;
        this.userAvatarRepository = userAvatarRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<User>> getUser(@RequestParam UUID uuid) {
        Optional<User> user = userRepository.findById(uuid);
        return user.map(value -> ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(value))).orElseGet(() -> ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "Provided user not found")));

    }

    @PutMapping
    public ResponseEntity<ApiResponse<User>> updateUser(@RequestBody User.Update payload, HttpSession session) throws JsonProcessingException {
        Optional<User> requestedPayloadUser = userRepository.findById(payload.getUuid());
        if (requestedPayloadUser.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Requested user not found"));
        }
        User user = requestedPayloadUser.get();

        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!optSessionUser.get().getUuid().equals(user.getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't edit different users"));
        }

        user.update(payload);

        session.setAttribute("user", objectMapper.writeValueAsString(user));

        return ResponseEntity
                .status(200)
                .body(new ApiResponse<>(userRepository.save(user)));
    }

}
