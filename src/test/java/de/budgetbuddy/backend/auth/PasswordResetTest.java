package de.budgetbuddy.backend.auth;

import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.MailService;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PasswordResetTest {
    private final UserRepository userRepository;
    private final UserPasswordResetRepository userPasswordResetRepository;
    private final AuthController authController;

    PasswordResetTest() {
        this.userRepository = mock(UserRepository.class);
        this.userPasswordResetRepository = mock(UserPasswordResetRepository.class);
        this.authController = new AuthController(userRepository,
                userPasswordResetRepository,
                new MailService(mock(Environment.class)));
    }

    @Test
    void requestPasswordReset_UserByEmailNotFound() {
        User user = new User(UUID.randomUUID());
        user.setEmail("test@budget-buddy.de");

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<UserPasswordReset>> response =
                authController.requestPasswordReset(user.getEmail());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("No user found for the provided email",
                Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void requestPasswordReset_Success() {
        User user = new User(UUID.randomUUID());
        user.setEmail("test@budget-buddy.de");

        UserPasswordReset userPasswordReset = new UserPasswordReset(user);

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(userPasswordResetRepository.save(userPasswordReset))
                .thenReturn(userPasswordReset);

        ResponseEntity<ApiResponse<UserPasswordReset>> response =
                authController.requestPasswordReset(user.getEmail());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void validateOtp_OtpNotFound() {
        UUID otp = UUID.randomUUID();

        when(userPasswordResetRepository.findByOtp(otp))
                .thenReturn(Optional.empty());

        ApiResponse<Boolean> result = authController.isOtpValid(otp);

        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatus());
        assertEquals("No session found for the provided session", result.getMessage());
        assertFalse(result.getData());
    }

    @Test
    void validateOtp_OtpHasExpired() {
        User user = new User(UUID.randomUUID());
        UserPasswordReset passwordReset = new UserPasswordReset(user);
        passwordReset.setCreatedAt(new Date(23, 11, 30));

        when(userPasswordResetRepository.findByOtp(passwordReset.getOtp()))
                .thenReturn(Optional.of(passwordReset));

        ApiResponse<Boolean> result = authController.isOtpValid(passwordReset.getOtp());

        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatus());
        assertEquals("This OTP has expired", result.getMessage());
        assertFalse(result.getData());
    }

    @Test
    void validateOtp_WasUsed() {
        User user = new User(UUID.randomUUID());
        UserPasswordReset passwordReset = new UserPasswordReset(user);
        passwordReset.setUsed(true);

        when(userPasswordResetRepository.findByOtp(passwordReset.getOtp()))
                .thenReturn(Optional.of(passwordReset));

        ApiResponse<Boolean> result = authController.isOtpValid(passwordReset.getOtp());

        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatus());
        assertEquals("This OTP has already been used", result.getMessage());
        assertFalse(result.getData());
    }

    @Test
    void validateOtp_Success() {
        User user = new User(UUID.randomUUID());
        UserPasswordReset passwordReset = new UserPasswordReset(user);

        when(userPasswordResetRepository.findByOtp(passwordReset.getOtp()))
                .thenReturn(Optional.of(passwordReset));

        ApiResponse<Boolean> result = authController.isOtpValid(passwordReset.getOtp());

        assertEquals(HttpStatus.OK.value(), result.getStatus());
        assertNull(result.getMessage());
        assertTrue(result.getData());
    }
}
