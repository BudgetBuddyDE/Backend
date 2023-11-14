package de.budgetbuddy.backend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {
    private final ObjectMapper objMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final UserRepository userRepository;

    @Autowired
    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping(value = "/register")
    public ResponseEntity<ApiResponse<User>> register(@RequestBody User user) {
        Optional<User> optionalUser = userRepository.findByEmail(user.getEmail());
        if (optionalUser.isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "This email is already in use"));
        }

        user.hashPassword();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(userRepository.save(user)));
    }

    @PostMapping(value = "/login")
    public ResponseEntity<ApiResponse<User>> login(@RequestBody User user, HttpSession session) {
        Optional<User> userByEmail = userRepository.findByEmail(user.getEmail());
        if (userByEmail.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "There is no user registered under this email address"));
        }

        User savedUser = userByEmail.get();
        if (!BCrypt.checkpw(user.getPassword(), savedUser.getPassword())) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), "The provided password is incorrect"));
        }

        try {
            session.setAttribute("user", objMapper.writeValueAsString(savedUser));
        } catch (JsonProcessingException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "You're logged in", savedUser));
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(HttpStatus.OK.value(), "You're logged in", savedUser));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<User>> validateSession(HttpSession session) {
        Object userObj = session.getAttribute("user");
        if (userObj == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(),  "Couldn't find a active session"));
        }

        try {
            User sessionUser = objMapper.readValue(session.getAttribute("user").toString(), User.class);
            // In order to always return the most updated version, we're gonna select the user again instead of relying on the session-user
            Optional<User> user = userRepository.findById(sessionUser.getUuid());
            if (user.isEmpty()) {
                session.invalidate();
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), "Your user has been deleted"));
            }

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ApiResponse<>("Found a valid session", user.get()));
        } catch (JsonProcessingException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Something went wrong on our side"));

        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logoutUser(HttpSession session) {
        session.invalidate();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(HttpStatus.OK.value(), "Your session has been destroyed"));
    }
}