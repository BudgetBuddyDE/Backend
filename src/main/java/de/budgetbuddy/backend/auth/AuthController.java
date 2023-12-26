package de.budgetbuddy.backend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.MailService;
import de.budgetbuddy.backend.log.Log;
import de.budgetbuddy.backend.log.LogType;
import de.budgetbuddy.backend.log.Logger;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import de.budgetbuddy.backend.user.role.Role;
import de.budgetbuddy.backend.user.role.RolePermission;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {
    private final ObjectMapper objMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final UserRepository userRepository;
    private final UserPasswordResetRepository userPasswordResetRepository;
    private final MailService mailService;

    @Autowired
    public AuthController(
            UserRepository userRepository,
            UserPasswordResetRepository userPasswordResetRepository,
            MailService mailService) {
        this.userRepository = userRepository;
        this.userPasswordResetRepository = userPasswordResetRepository;
        this.mailService = mailService;
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
        try {
            if (!mailService.trigger(MailService.getVerificationMailPayload(savedUser))) {
                Logger.getInstance()
                        .log(new Log("Backend", LogType.WARNING, "registration", "Couldn't send the verification email"));
            }
        } catch (Exception e) {
            System.out.print(e.getMessage());
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

    @PostMapping("/password/request-reset")
    public ResponseEntity<ApiResponse<UserPasswordReset>> requestPasswordReset(
            @RequestParam String email) {
        Optional<User> optUser = userRepository.findByEmail(email);

        if (optUser.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "No user found for the provided email"));
        }

        UserPasswordReset passwordReset = userPasswordResetRepository.save(new UserPasswordReset(optUser.get()));

        try {
            if (!mailService.trigger(MailService.getRequestPasswordMailPayload(
                    email,
                    optUser.get().getUuid(),
                    passwordReset.getOtp()))) {
                Logger.getInstance()
                        .log(new Log("Backend", LogType.WARNING, "password-reset", "Couldn't send the request-password-change email"));
            }
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(passwordReset));
    }

    @PostMapping("/password/validate-otp")
    public ResponseEntity<ApiResponse<Boolean>> validatePasswordRequestOtp(
            @RequestParam UUID otp
    ) {
        ApiResponse<Boolean> validationResult = this.isOtpValid(otp);
        return ResponseEntity
                .status(validationResult.getStatus())
                .body(validationResult);
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<User>> resetPassword(
            @RequestParam UUID otp,
            @RequestParam String newPassword
    ) {
        ApiResponse<Boolean> validationResult = this.isOtpValid(otp);
        if (!validationResult.getData()) {
            return ResponseEntity
                    .status(validationResult.getStatus())
                    .body(new ApiResponse<>(validationResult.getStatus(), validationResult.getMessage()));
        }

        UserPasswordReset passwordReset = userPasswordResetRepository.findByOtp(otp).get();
        passwordReset.setUsed(true);
        userPasswordResetRepository.save(passwordReset);

        User user = passwordReset.getOwner();
        user.setPassword(newPassword);
        user.hashPassword();
        userRepository.save(user);

        try {
            if (!mailService.trigger(MailService.getPasswordChangedMailPayload(
                    user.getEmail(),
                    user.getName(),
                    user.getEmail()))) {
                Logger.getInstance()
                        .log(new Log("Backend", LogType.WARNING, "password-reset", "Couldn't send the password-changed notification email"));
            }
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(user));
    }

    public ApiResponse<Boolean> isOtpValid(UUID otp) {
        Optional<UserPasswordReset> optPasswordRequest = userPasswordResetRepository.findByOtp(otp);
        if (optPasswordRequest.isEmpty()) {
            return new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "No session found for the provided session",
                    false);
        }

        UserPasswordReset passwordReset = optPasswordRequest.get();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime otpCreatedAt = passwordReset
                .getCreatedAt()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (ChronoUnit.DAYS.between(otpCreatedAt, now) >= 1) {
            return new ApiResponse<>(
                            HttpStatus.UNAUTHORIZED.value(),
                            "This OTP has expired",
                            false);
        }

        if (passwordReset.wasUsed()) {
            return new ApiResponse<>(
                    HttpStatus.UNAUTHORIZED.value(),
                    "This OTP has already been used",
                    false);
        }

        return new ApiResponse<>(true);
    }

}