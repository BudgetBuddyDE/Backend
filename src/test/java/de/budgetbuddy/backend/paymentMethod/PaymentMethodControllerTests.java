package de.budgetbuddy.backend.paymentMethod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
public class PaymentMethodControllerTests {
    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentMethodController paymentMethodController;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockHttpSession session;

    PaymentMethodControllerTests() {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        PaymentMethodRepository paymentMethodRepository = Mockito.mock(PaymentMethodRepository.class);
        this.userRepository = userRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentMethodController = new PaymentMethodController(paymentMethodRepository, userRepository);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        session = new MockHttpSession();
    }

    @Test
    void testCreatePaymentMethod_UserNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        PaymentMethod.Create payload = new PaymentMethod.Create();
        payload.setOwner(uuid);

        when(userRepository.findById(uuid)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<PaymentMethod>> response = paymentMethodController.createPaymentMethod(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided user doesn't exist", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreatePaymentMethod_PaymentMethodAlreadyExists() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        PaymentMethod.Create payload = new PaymentMethod.Create();
        payload.setOwner(uuid);
        payload.setName("Shopping");
        payload.setAddress("DE12-3456-7890");

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setName(paymentMethod.getName());
        paymentMethod.setDescription(paymentMethod.getDescription());

        User user = new User();
        user.setUuid(uuid);

        when(userRepository.findById(uuid))
                .thenReturn(Optional.of(user));
        when(paymentMethodRepository.findByOwnerAndNameAndAddress(user, payload.getName(), payload.getAddress()))
                .thenReturn(Optional.of(paymentMethod));

        ResponseEntity<ApiResponse<PaymentMethod>> response = paymentMethodController.createPaymentMethod(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("There is already an payment-method by this name and address",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreatePaymentMethod_TryForDiffSessionUser() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        PaymentMethod.Create payload = new PaymentMethod.Create();
        payload.setOwner(uuid);
        payload.setName("Shopping");
        payload.setAddress("DE12-3456-7890");

        User user = new User();
        user.setUuid(uuid);

        PaymentMethod paymentMethod = new PaymentMethod(user, payload.getName(), payload.getAddress(), payload.getAddress(), null);

        User sessionUser = new User();
        sessionUser.setUuid(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));
        when(paymentMethodRepository.findByOwnerAndNameAndAddress(user, payload.getName(), payload.getAddress()))
                .thenReturn(Optional.empty());
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenReturn(paymentMethod);

        ResponseEntity<ApiResponse<PaymentMethod>> response = paymentMethodController.createPaymentMethod(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't create payment-methods for other users",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreatePaymentMethod_Success() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        PaymentMethod.Create payload = new PaymentMethod.Create();
        payload.setOwner(uuid);
        payload.setName("Shopping");
        payload.setAddress("DE12-3456-7890");

        User user = new User();
        user.setUuid(uuid);

        session.setAttribute("user", objectMapper.writeValueAsString(user));

        PaymentMethod paymentMethod = new PaymentMethod(user, payload.getName(), payload.getAddress(), payload.getProvider(), null);

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));
        when(paymentMethodRepository.findByOwnerAndNameAndAddress(user, payload.getName(), paymentMethod.getAddress()))
                .thenReturn(Optional.empty());
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenReturn(paymentMethod);

        ResponseEntity<ApiResponse<PaymentMethod>> response = paymentMethodController.createPaymentMethod(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(paymentMethod, Objects.requireNonNull(response.getBody()).getData());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void testGetPaymentMethods_ValidSession() throws JsonProcessingException {
        List<PaymentMethod> paymentMethods = new ArrayList<>();
        UUID uuid = UUID.randomUUID();
        User user = new User();
        user.setUuid(uuid);
        user.setEmail("test@test.com");
        user.setPassword("test");

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));
        when(paymentMethodRepository.findAllByOwner(user)).thenReturn(paymentMethods);

        session.setAttribute("user", objectMapper.writeValueAsString(user));
        ResponseEntity<ApiResponse<List<PaymentMethod>>> response = paymentMethodController.getPaymentMethodsByUuid(uuid);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(paymentMethods, Objects.requireNonNull(response.getBody()).getData());
    }


    @Test
    void testUpdatePaymentMethod_PaymentMethodNotFound() throws JsonProcessingException {
        PaymentMethod.Update payload = new PaymentMethod.Update();
        payload.setId(1L);

        when(paymentMethodRepository.findById(payload.getId())).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<PaymentMethod>> response = paymentMethodController.updatePaymentMethod(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided payment-method doesn't exist", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdatePaymentMethod_PaymentMethodNameAndAddressAlreadyUsed() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        PaymentMethod pm1 = new PaymentMethod();
        pm1.setId(1L);
        pm1.setName("Shopping");
        pm1.setAddress("ADDRESS");
        pm1.setOwner(user);

        PaymentMethod pm2 = new PaymentMethod();
        pm2.setId(2L);
        pm2.setName("Not Shopping");
        pm2.setAddress("ADDRESS");
        pm2.setOwner(user);

        PaymentMethod.Update payload = new PaymentMethod.Update();
        payload.setId(pm2.getId());
        payload.setName(pm1.getName());
        payload.setAddress(pm1.getAddress());

        when(paymentMethodRepository.findByOwnerAndNameAndAddress(user, payload.getName(), payload.getAddress())).
                thenReturn(Optional.of(pm1));
        when(paymentMethodRepository.findById(pm2.getId())).thenReturn(Optional.of(pm2));

        session.setAttribute("user", objectMapper.writeValueAsString(user));

        ResponseEntity<ApiResponse<PaymentMethod>> response = paymentMethodController.updatePaymentMethod(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("There is already an payment-method by this name and address", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdatePaymentMethod_TryForDiffUser() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        User sessionUser = new User();
        sessionUser.setUuid(UUID.randomUUID());

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(1L);
        paymentMethod.setOwner(user);
        paymentMethod.setName("Not Shopping");
        paymentMethod.setAddress("ADDRESS");

        PaymentMethod.Update payload = new PaymentMethod.Update();
        payload.setId(paymentMethod.getId());
        payload.setName("Shopping");
        payload.setAddress(paymentMethod.getAddress());

        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(paymentMethod));
        when(paymentMethodRepository.findByOwnerAndNameAndAddress(user, payload.getName(), payload.getAddress()))
                .thenReturn(Optional.empty());
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenReturn(paymentMethod);

        ResponseEntity<ApiResponse<PaymentMethod>> response = paymentMethodController.updatePaymentMethod(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't modify payment-methods from other users", Objects
                .requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdatePaymentMethod_Success() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        PaymentMethod paymentmethod = new PaymentMethod();
        paymentmethod.setId(1L);
        paymentmethod.setOwner(user);
        paymentmethod.setName("Not Shopping");
        paymentmethod.setAddress("ADDRESS");

        PaymentMethod.Update payload = new PaymentMethod.Update();
        payload.setId(1L);
        payload.setName("Shopping");
        payload.setAddress(payload.getAddress());

        PaymentMethod updatedPaymentMethod = new PaymentMethod();
        updatedPaymentMethod.setId(1L);
        updatedPaymentMethod.setOwner(user);
        updatedPaymentMethod.setName("Shopping");
        updatedPaymentMethod.setAddress("ADDRESS");

        session.setAttribute("user", objectMapper.writeValueAsString(user));

        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(paymentmethod));
        when(paymentMethodRepository.findByOwnerAndNameAndAddress(user, payload.getName(), payload.getAddress()))
                .thenReturn(Optional.empty());
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenReturn(updatedPaymentMethod);

        ResponseEntity<ApiResponse<PaymentMethod>> response = paymentMethodController.updatePaymentMethod(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(updatedPaymentMethod, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeletePaymentMethod_EmptyList() throws JsonProcessingException {
        List<PaymentMethod.Delete> payload = new ArrayList<>();

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                paymentMethodController.deletePaymentMethods(payload, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No payment-methods we're provided",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeletePaymentMethod_AllItemsInvalid() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        List<PaymentMethod.Delete> payload = new ArrayList<>();
        payload.add(PaymentMethod.builder()
                .id(1L)
                .owner(owner)
                .build()
                .toDelete());


        when(paymentMethodRepository.findById(any(Long.class)))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                paymentMethodController.deletePaymentMethods(payload, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("All provided payment-methods we're invalid values",
                Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(0, responseBody.get("success").size());
    }

    @Test
    void testDeletePaymentMethod_SomeFailures() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        List<PaymentMethod.Delete> payload = new ArrayList<>();
        PaymentMethod pm1 = PaymentMethod.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(pm1.toDelete());

        PaymentMethod pm2 = PaymentMethod.builder()
                .id(2L)
                .build();
        payload.add(pm2.toDelete());

        when(paymentMethodRepository.findById(1L))
                .thenReturn(Optional.of(pm1));
        when(paymentMethodRepository.findById(2L))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                paymentMethodController.deletePaymentMethods(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(1, responseBody.get("success").size());
    }

    @Test
    void testDeletePaymentMethod_WrongSessionUser() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        List<PaymentMethod.Delete> payload = new ArrayList<>();
        PaymentMethod pm1 = PaymentMethod.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(pm1.toDelete());

        PaymentMethod pm2 = PaymentMethod.builder()
                .id(2L)
                .owner(sessionUser)
                .build();
        payload.add(pm2.toDelete());

        when(paymentMethodRepository.findById(1L))
                .thenReturn(Optional.of(pm1));
        when(paymentMethodRepository.findById(2L))
                .thenReturn(Optional.of(pm2));

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                paymentMethodController.deletePaymentMethods(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(1, responseBody.get("success").size());
    }

    @Test
    void testDeletePaymentMethod_Success() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        List<PaymentMethod.Delete> payload = new ArrayList<>();
        PaymentMethod pm1 = PaymentMethod.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(pm1.toDelete());

        PaymentMethod pm2 = PaymentMethod.builder()
                .id(2L)
                .owner(owner)
                .build();
        payload.add(pm2.toDelete());

        when(paymentMethodRepository.findById(1L))
                .thenReturn(Optional.of(pm1));
        when(paymentMethodRepository.findById(2L))
                .thenReturn(Optional.of(pm2));

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                paymentMethodController.deletePaymentMethods(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(0, responseBody.get("failed").size());
        assertEquals(2, responseBody.get("success").size());
    }

}
