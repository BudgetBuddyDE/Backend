package de.budgetbuddy.backend.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.category.CategoryRepository;
import de.budgetbuddy.backend.paymentMethod.PaymentMethod;
import de.budgetbuddy.backend.paymentMethod.PaymentMethodRepository;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
public class SubscriptionControllerTests {
    @Autowired
    private MockMvc mockMvc;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionController subscriptionController;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockHttpSession session;

    SubscriptionControllerTests() {
        this.userRepository = Mockito.mock(UserRepository.class);
        this.categoryRepository = Mockito.mock(CategoryRepository.class);
        this.paymentMethodRepository = Mockito.mock(PaymentMethodRepository.class);
        this.subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
        this.subscriptionController = new SubscriptionController(userRepository, categoryRepository, paymentMethodRepository, subscriptionRepository);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        session = new MockHttpSession();
    }

    @Test
    void testCreateSubscription_InvalidSession() throws Exception {
        session.invalidate();
        mockMvc
                .perform(MockMvcRequestBuilders
                        .post("/v1/subscription")
                        .session(session)
                )
                .andExpect(MockMvcResultMatchers.status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testCreateSubscription_InvalidExecDate0() throws JsonProcessingException {
        Subscription.Create payload = new Subscription.Create();
        payload.setExecuteAt(0);

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.createSubscription(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Execution must lay between the first and 31nd of the month",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateSubscription_InvalidExecDate32() throws JsonProcessingException {
        Subscription.Create payload = new Subscription.Create();
        payload.setExecuteAt(32);

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.createSubscription(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Execution must lay between the first and 31nd of the month",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateSubscription_CategoryNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Subscription.Create payload = new Subscription.Create();
        payload.setOwner(uuid);
        payload.setExecuteAt(1);
        payload.setCategoryId(1L);

        when(userRepository.findById(uuid))
                .thenReturn(Optional.of(owner));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), owner))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.createSubscription(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided category not found",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateSubscription_PaymentMethodNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        Subscription.Create payload = new Subscription.Create();
        payload.setOwner(uuid);
        payload.setExecuteAt(1);
        payload.setCategoryId(category.getId());
        payload.setPaymentMethodId(1L);

        when(userRepository.findById(uuid))
                .thenReturn(Optional.of(owner));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), owner))
                .thenReturn(Optional.of(category));
        when(paymentMethodRepository.findByIdAndOwner(payload.getPaymentMethodId(), owner))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.createSubscription(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided payment-method not found",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateSubscription_PayloadOwnerNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        Subscription.Create payload = new Subscription.Create();
        payload.setExecuteAt(1);
        payload.setOwner(uuid);

        when(userRepository.findById(payload.getOwner()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.createSubscription(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided owner not found",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateSubscription_WrongSessionUser() throws JsonProcessingException {
        session.setAttribute("user", objectMapper.writeValueAsString(new User(UUID.randomUUID())));

        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);

        Subscription.Create payload = new Subscription.Create();
        payload.setOwner(uuid);
        payload.setExecuteAt(1);

        when(userRepository.findById(payload.getOwner()))
                .thenReturn(Optional.of(owner));

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.createSubscription(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Your subscription owner has to be your session-user",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateSubscription_Success() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(1L);
        paymentMethod.setOwner(owner);

        Subscription.Create payload = new Subscription.Create();
        payload.setOwner(uuid);
        payload.setExecuteAt(1);
        payload.setCategoryId(category.getId());
        payload.setPaymentMethodId(paymentMethod.getId());
        payload.setPaused(false);

        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setOwner(owner);
        subscription.setCategory(category);
        subscription.setPaymentMethod(paymentMethod);

        when(userRepository.findById(payload.getOwner()))
                .thenReturn(Optional.of(owner));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), owner))
                .thenReturn(Optional.of(category));
        when(paymentMethodRepository.findByIdAndOwner(payload.getPaymentMethodId(), owner))
                .thenReturn(Optional.of(paymentMethod));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenReturn(subscription);

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.createSubscription(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(subscription, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetSubscription_InvalidSession() throws Exception {
        session.invalidate();
        mockMvc
                .perform(MockMvcRequestBuilders
                        .get("/v1/subscription")
                        .session(session)
                )
                .andExpect(MockMvcResultMatchers.status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testGetSubscription_UserNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        List<Subscription> subscriptionList = new ArrayList<>();
        when(userRepository.findById(uuid))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<List<Subscription>>> response = subscriptionController.getSubscriptionsByUuid(uuid, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided user doesn't exist",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(subscriptionList, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetSubscription_WrongSessionUser() throws JsonProcessingException {
        session.setAttribute("user", objectMapper.writeValueAsString(new User(UUID.randomUUID())));

        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        List<Subscription> subscriptionList = new ArrayList<>();

        when(userRepository.findById(uuid)).thenReturn(Optional.of(owner));

        ResponseEntity<ApiResponse<List<Subscription>>> response = subscriptionController.getSubscriptionsByUuid(uuid, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't retrieve subscriptions of different users",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(subscriptionList, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetSubscription_Success() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        List<Subscription> subscriptionList = new ArrayList<>();

        when(userRepository.findById(uuid))
                .thenReturn(Optional.of(owner));
        when(subscriptionRepository.findAllByOwner(owner))
                .thenReturn(subscriptionList);

        ResponseEntity<ApiResponse<List<Subscription>>> response = subscriptionController.getSubscriptionsByUuid(uuid, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(subscriptionList, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateSubscription_InvalidSession() throws Exception {
        session.invalidate();
        mockMvc
                .perform(MockMvcRequestBuilders
                        .put("/v1/subscription")
                        .session(session)
                )
                .andExpect(MockMvcResultMatchers.status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testUpdateSubscription_InvalidExecDate0() throws JsonProcessingException {
        Subscription.Update payload = new Subscription.Update();
        payload.setExecuteAt(0);

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.updateSubscription(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Execution must lay between the first and 31nd of the month",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateSubscription_InvalidExecDate32() throws JsonProcessingException {
        Subscription.Update payload = new Subscription.Update();
        payload.setExecuteAt(32);

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.updateSubscription(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Execution must lay between the first and 31nd of the month",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateSubscription_SubscriptionNotFound() throws JsonProcessingException {
        Subscription.Update payload = new Subscription.Update();
        payload.setSubscriptionId(1L);
        payload.setExecuteAt(1);

        when(subscriptionRepository.findById(payload.getSubscriptionId()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Subscription>> response =    subscriptionController.updateSubscription(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided subscription not found", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateSubscription_CategoryNotFound() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setOwner(owner);

        Subscription.Update payload = new Subscription.Update();
        payload.setExecuteAt(1);
        payload.setSubscriptionId(1L);
        payload.setCategoryId(1L);

        when(subscriptionRepository.findById(payload.getSubscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(categoryRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.updateSubscription(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided category not found", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateSubscription_PaymentMethodNotFound() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setOwner(owner);

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        Subscription.Update payload = new Subscription.Update();
        payload.setExecuteAt(1);
        payload.setSubscriptionId(1L);
        payload.setCategoryId(1L);

        when(subscriptionRepository.findById(payload.getSubscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(categoryRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.of(category));
        when(paymentMethodRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.updateSubscription(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided payment-method not found", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateSubscription_WrongSessionUser() throws JsonProcessingException {
        session.setAttribute("user", objectMapper.writeValueAsString(new User(UUID.randomUUID())));

        User owner = new User(UUID.randomUUID());

        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setOwner(owner);

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(1L);
        paymentMethod.setOwner(owner);

        Subscription.Update payload = new Subscription.Update();
        payload.setExecuteAt(1);
        payload.setSubscriptionId(1L);
        payload.setCategoryId(1L);
        payload.setPaymentMethodId(1L);

        when(subscriptionRepository.findById(payload.getSubscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(categoryRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.of(category));
        when(paymentMethodRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.of(paymentMethod));

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.updateSubscription(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You don't own this subscription",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateSubscription_Success() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setOwner(owner);
        subscription.setPaused(true);

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(1L);
        paymentMethod.setOwner(owner);

        Subscription.Update payload = new Subscription.Update();
        payload.setSubscriptionId(1L);
        payload.setExecuteAt(1);
        payload.setCategoryId(1L);
        payload.setPaymentMethodId(1L);
        payload.setPaused(false);

        Subscription updatedSubscription = new Subscription();
        updatedSubscription.setId(1L);
        updatedSubscription.setOwner(owner);
        updatedSubscription.setPaused(false);

        when(subscriptionRepository.findById(payload.getSubscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(categoryRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.of(category));
        when(paymentMethodRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.of(paymentMethod));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenReturn(updatedSubscription);

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.updateSubscription(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(updatedSubscription, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteSubscription_InvalidSession() throws Exception {
        session.invalidate();
        mockMvc
                .perform(MockMvcRequestBuilders
                        .delete("/v1/subscription")
                        .session(session)
                )
                .andExpect(MockMvcResultMatchers.status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testDeleteSubscription_TransactionNotFound() throws JsonProcessingException {
        Subscription.Delete payload = new Subscription.Delete();
        payload.setSubscriptionId(1L);

        when(subscriptionRepository.findById(payload.getSubscriptionId()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.deleteSubscription(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided subscription not found", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteSubscription_WrongSessionUser() throws JsonProcessingException {
        session.setAttribute("user", objectMapper.writeValueAsString(new User(UUID.randomUUID())));

        User owner = new User(UUID.randomUUID());

        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setOwner(owner);

        Subscription.Delete payload = new Subscription.Delete();
        payload.setSubscriptionId(1L);

        when(subscriptionRepository.findById(payload.getSubscriptionId()))
                .thenReturn(Optional.of(subscription));

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.deleteSubscription(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You don't own this subscription",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteSubscription_Success() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setOwner(owner);

        Subscription.Delete payload = new Subscription.Delete();
        payload.setSubscriptionId(1L);

        when(subscriptionRepository.findById(payload.getSubscriptionId()))
                .thenReturn(Optional.of(subscription));

        ResponseEntity<ApiResponse<Subscription>> response = subscriptionController.deleteSubscription(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(subscription, Objects.requireNonNull(response.getBody()).getData());
    }
}
