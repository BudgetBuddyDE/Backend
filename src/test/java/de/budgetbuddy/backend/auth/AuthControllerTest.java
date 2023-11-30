package de.budgetbuddy.backend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private AuthController authController;
    private MockHttpSession session;
    private final ObjectMapper objMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        session = new MockHttpSession();
    }

    @Test
    void registerUserAlreadyExists() {
        User user = new User();
        user.setEmail("test@test.com");

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        ResponseEntity<ApiResponse<User>> response = authController.register(user);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("This email is already in use", Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void registerUserNew() {
        User user = new User();
        user.setEmail("test@test.com");

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.empty());
        when(userRepository.save(user)).thenReturn(user);

        ResponseEntity<ApiResponse<User>> response = authController.register(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(user, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void loginUserNotFound() {
        User user = new User();
        user.setEmail("test@test.com");

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<User>> response = authController.login(user, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("There is no user registered under this email address", Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void loginUserFoundWithInvalidCredentials() {
        User user = new User();
        user.setEmail("test@test.com");
        user.setPassword("test");
        user.hashPassword();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        User providedUser = new User();
        providedUser.setEmail("test@test.com");
        providedUser.setPassword("test1");
        ResponseEntity<ApiResponse<User>> response = authController.login(providedUser, session);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("The provided password is incorrect", Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void successfullLogin() throws JsonProcessingException {
        User user = new User();
        user.setEmail("test@test.com");
        user.setPassword("test");
        user.hashPassword();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        User loginUser = new User();
        loginUser.setEmail("test@test.com");
        loginUser.setPassword("test");
        ResponseEntity<ApiResponse<User>> response = authController.login(loginUser, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("You're logged in", Objects.requireNonNull(response.getBody()).getMessage());

    }

    @Test
    void testValidateSession_NoValidSession() {
        ResponseEntity<ApiResponse<User>> response = authController.validateSession(session);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Couldn't find a active session", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testValidateSession_ValidSessionFound() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        when(userRepository.findById(sessionUser.getUuid())).thenReturn(Optional.of(sessionUser));

        ResponseEntity<ApiResponse<User>> response = authController.validateSession(session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Found a valid session", Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(objMapper.readValue(session.getAttribute("user").toString(), User.class), sessionUser);
    }

    @Test
    void testLogoutUser_LoggedOut() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        ResponseEntity<ApiResponse<String>> response = authController.logoutUser(session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Your session has been destroyed", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }
}