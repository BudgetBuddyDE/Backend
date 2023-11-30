package de.budgetbuddy.backend.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
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
import static org.mockito.Mockito.when;


public class UserControllerTests {
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserController userController;
    private MockHttpSession session;
    private final ObjectMapper objMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        session = new MockHttpSession();
    }

    @Test
    void testUpdateUser_UserNotFound() throws JsonProcessingException {
        User.Update payload = new User.Update();
        payload.setUuid(UUID.randomUUID());

        when(userRepository.findById(payload.getUuid()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<User>> response = userController.updateUser(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
        assertEquals("Requested user not found", Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void testUpdateUser_WrongSessionUser() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        User user = new User(UUID.randomUUID());
        User.Update payload = new User.Update();
        payload.setUuid(user.getUuid());

        when(userRepository.findById(payload.getUuid()))
                .thenReturn(Optional.of(user));

        ResponseEntity<ApiResponse<User>> response = userController.updateUser(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
        assertEquals("You can't edit different users", Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void testUpdateUser_Success() throws JsonProcessingException {
        User user = new User(UUID.randomUUID());
        user.setName("Before Update");

        session.setAttribute("user", objMapper.writeValueAsString(user));

        User.Update payload = new User.Update();
        payload.setUuid(user.getUuid());
        payload.setName("After Update");

        User updatedUser = new User();
        updatedUser.setUuid(user.getUuid());
        updatedUser.setName(payload.getName());

        when(userRepository.findById(payload.getUuid()))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(updatedUser);

        ResponseEntity<ApiResponse<User>> response = userController.updateUser(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(updatedUser, Objects.requireNonNull(response.getBody()).getData());
        assertTrue(updatedUser.equals(Objects.requireNonNull(response.getBody()).getData()));
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
    }
}
