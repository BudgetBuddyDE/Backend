package de.budgetbuddy.backend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.MailService;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import de.budgetbuddy.backend.user.role.Role;
import de.budgetbuddy.backend.user.role.RolePermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private final UserRepository userRepository;
    private final AuthController authController;
    private MockHttpSession session;
    private final ObjectMapper objMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    AuthControllerTest() {
        this.userRepository = mock(UserRepository.class);
        UserPasswordResetRepository userPasswordResetRepository = mock(UserPasswordResetRepository.class);
        Environment environment = mock(Environment.class);
        MailService mailService = new MailService(environment);
        this.authController = new AuthController(userRepository, userPasswordResetRepository, mailService);
    }

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

        ResponseEntity<ApiResponse<User>> response = authController.register(user, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("This email is already in use", Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void testRegisterAdmin_MissingAuthHeader() {
        User user = new User(UUID.randomUUID());
        user.setEmail("test@mail.com");
        user.setRole(new Role(RolePermission.SERVICE_ACCOUNT));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        ResponseEntity<ApiResponse<User>> response = authController.register(user, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("You need to verify yourself in order to proceed",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testRegisterAdmin_InvalidAuthHeaderValues() {
        User authUser = new User(UUID.randomUUID());
        authUser.setPassword("hashedPassword");
        authUser.setRole(new Role(RolePermission.SERVICE_ACCOUNT));

        when(userRepository.findByUuidAndPassword(authUser.getUuid(), "anotherHashedPassword"))
                .thenReturn(Optional.empty());

        User user = new User(UUID.randomUUID());
        user.setEmail("test@mail.com");
        user.setRole(new Role(RolePermission.SERVICE_ACCOUNT));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        ResponseEntity<ApiResponse<User>> response = authController
                .register(user, String.format("Bearer %s.%s", authUser.getUuid().toString(), authUser.getPassword()));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Your provided credentials are invalid",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testRegisterAdmin_InvalidPermissions() {
        User authUser = new User(UUID.randomUUID());
        authUser.setPassword("hashedPassword");
        authUser.setRole(new Role(RolePermission.SERVICE_ACCOUNT));

        when(userRepository.findByUuidAndPassword(authUser.getUuid(), authUser.getPassword()))
                .thenReturn(Optional.of(authUser));

        User user = new User(UUID.randomUUID());
        user.setEmail("test@mail.com");
        user.setRole(new Role(RolePermission.SERVICE_ACCOUNT));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        ResponseEntity<ApiResponse<User>> response = authController
                .register(user, String.format("Bearer %s.%s", authUser.getUuid().toString(), authUser.getPassword()));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("You don't have the permissions to create this user",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testRegisterAdmin_Success() {
        User authUser = new User(UUID.randomUUID());
        authUser.setPassword("hashedPassword");
        authUser.setRole(new Role(RolePermission.ADMIN));

        when(userRepository.findByUuidAndPassword(authUser.getUuid(), authUser.getPassword()))
                .thenReturn(Optional.of(authUser));

        User user = new User(UUID.randomUUID());
        user.setEmail("test@mail.com");
        user.setRole(new Role(RolePermission.SERVICE_ACCOUNT));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        ResponseEntity<ApiResponse<User>> response = authController
                .register(user, String.format("Bearer %s.%s", authUser.getUuid().toString(), authUser.getPassword()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(user, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void registerUserNew() {
        User user = new User(UUID.randomUUID());
        user.setEmail("test@test.com");

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.empty());
        when(userRepository.save(user)).thenReturn(user);

        ResponseEntity<ApiResponse<User>> response = authController.register(user, null);

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
    void successfullLogin() {
        User user = new User();
        user.setUuid(UUID.randomUUID());
        user.setEmail("test@test.com");
        user.setPassword("test");
        user.hashPassword();

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        User loginUser = new User(user.getUuid());
        loginUser.setEmail(user.getEmail());
        loginUser.setPassword("test"); // enter password in plain text, because otherwise it would be hashed again

        ResponseEntity<ApiResponse<User>> response = authController.login(loginUser, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("You're logged in",
                Objects.requireNonNull(response.getBody()).getMessage());
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

        when(userRepository.findById(sessionUser.getUuid()))
                .thenReturn(Optional.of(sessionUser));

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

    @Test
    void testVerifyToken_InvalidFormat() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ResponseEntity<ApiResponse<User>> response = authController
                    .validateBearerToken("token");
        });

        String expectedMessage = "Invalid Authorization header format";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    void testVerifyToken_InvalidValue() {
        UUID uuid = UUID.randomUUID();
        String password = "password";

        AuthorizationInterceptor.AuthValues authValues = new AuthorizationInterceptor
                .AuthValues(uuid, password);

        when(userRepository.findByUuidAndPassword(authValues.getUuid(), authValues.getHashedPassword()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<User>> response = authController
                .validateBearerToken(authValues.getBearerToken());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Provided Bearer-Token is invalid",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testVerifyToken_Success() {
        User user = new User(UUID.randomUUID());
        user.setPassword("password");
        user.hashPassword();

        AuthorizationInterceptor.AuthValues authValues = new AuthorizationInterceptor
                .AuthValues(user.getUuid(), user.getPassword());

        when(userRepository.findByUuidAndPassword(authValues.getUuid(), authValues.getHashedPassword()))
                .thenReturn(Optional.of(user));

        ResponseEntity<ApiResponse<User>> response = authController
                .validateBearerToken(authValues.getBearerToken());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(user, Objects.requireNonNull(response.getBody()).getData());
    }
}