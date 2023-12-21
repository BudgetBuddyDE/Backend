package de.budgetbuddy.backend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.WebhookTrigger;
import de.budgetbuddy.backend.log.Log;
import de.budgetbuddy.backend.log.LogType;
import de.budgetbuddy.backend.log.Logger;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import de.budgetbuddy.backend.user.role.Role;
import de.budgetbuddy.backend.user.role.RolePermission;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {
    private final ObjectMapper objMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final UserRepository userRepository;
    private final Environment environment;

    @Autowired
    public AuthController(UserRepository userRepository, Environment env) {
        this.userRepository = userRepository;
        this.environment = env;
    }

    @PostMapping(value = "/register")
    public ResponseEntity<ApiResponse<User>> register(
            @RequestBody User user,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        Optional<User> optionalUser = userRepository.findByEmail(user.getEmail());
        if (optionalUser.isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "This email is already in use"));
        }

        user.hashPassword();
        if (user.getRole() == null) {
            user.setRole(new Role(RolePermission.BASIC));
        } else if (user.getRole().isGreaterOrEqualThan(RolePermission.SERVICE_ACCOUNT)) {
            if (Objects.isNull(authorizationHeader) || authorizationHeader.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>(
                                HttpStatus.UNAUTHORIZED.value(),
                                "You need to verify yourself in order to proceed"));
            }

            AuthorizationInterceptor.AuthValues authValues = AuthorizationInterceptor
                    .retrieveTokenValue(authorizationHeader);
            if (authValues.getUuid() == null || authValues.getHashedPassword() == null) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>(
                                HttpStatus.UNAUTHORIZED.value(),
                                "Your provided Bearer-Token is not correctly formatted"));
            }

            Optional<User> authUser = userRepository
                    .findByUuidAndPassword(authValues.getUuid(), authValues.getHashedPassword());
            if (authUser.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>(
                                HttpStatus.UNAUTHORIZED.value(),
                                "Your provided credentials are invalid"));
            }

            if (!authUser.get().getRole().isGreaterOrEqualThan(RolePermission.ADMIN)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>(
                                HttpStatus.UNAUTHORIZED.value(),
                                "You don't have the permissions to create this user"));
            }
        }

        User savedUser = userRepository.save(user);

        if (!Objects.isNull(environment)) {
            String mailServiceHost = environment.getProperty("de.budget-buddy.mail-service.address");
            if (!Objects.isNull(mailServiceHost) && mailServiceHost.length() > 1) {
                JSONObject payload = new JSONObject();
                payload.put("to", savedUser.getEmail());
                payload.put("mail", "welcome");
                payload.put("uuid", savedUser.getUuid().toString());
                Boolean wasVerificationMailSent = WebhookTrigger.send(mailServiceHost + "/send", payload.toString());
                if (!wasVerificationMailSent) {
                    Logger.getInstance().log(new Log("Backend", LogType.WARNING, "Registration", "Couldn't send email-verification-mail"));
                }
            }
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(savedUser));
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

    @GetMapping("/verify")
    public RedirectView verifyMailAddress(
            @RequestParam String returnTo,
            @RequestParam UUID uuid,
            @RequestParam String mailAddress) {
        RedirectView redirectView = new RedirectView(returnTo);
        VerifyMailReturnCode returnCode = VerifyMailReturnCode.SUCCESS;
        Optional<User> optionalUser = userRepository.findById(uuid);
        if (optionalUser.isEmpty()) {
            returnCode = VerifyMailReturnCode.USER_NOT_FOUND;
        }

        User user = optionalUser.get();
        if (user.getIsVerified()) {
            returnCode = VerifyMailReturnCode.ALREADY_VERIFIED;
        }

        if (!user.getEmail().equals(mailAddress)) {
            returnCode = VerifyMailReturnCode.INVALID_EMAIL;
        }

        user.setIsVerified(true);
        userRepository.save(user);
        redirectView.addStaticAttribute("code", returnCode);

        return redirectView;
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logoutUser(HttpSession session) {
        session.invalidate();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(HttpStatus.OK.value(), "Your session has been destroyed"));
    }
}