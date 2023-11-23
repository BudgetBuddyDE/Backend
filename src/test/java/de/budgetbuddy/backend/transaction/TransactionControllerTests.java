package de.budgetbuddy.backend.transaction;

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
public class TransactionControllerTests {
    @Autowired
    private MockMvc mockMvc;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionController transactionController;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockHttpSession session;

    TransactionControllerTests() {
        this.userRepository = Mockito.mock(UserRepository.class);
        this.categoryRepository = Mockito.mock(CategoryRepository.class);
        this.paymentMethodRepository = Mockito.mock(PaymentMethodRepository.class);
        this.transactionRepository = Mockito.mock(TransactionRepository.class);
        this.transactionController = new TransactionController(userRepository, categoryRepository, paymentMethodRepository, transactionRepository);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        session = new MockHttpSession();
    }

    @Test
    void testCreateTransaction_InvalidSession() throws Exception {
        session.invalidate();
        mockMvc
                .perform(MockMvcRequestBuilders
                        .post("/v1/transaction")
                        .session(session)
                )
                .andExpect(MockMvcResultMatchers.status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testCreateTransaction_CategoryNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Transaction.Create payload = new Transaction.Create();
        payload.setOwner(uuid);
        payload.setCategoryId(1L);

        when(userRepository.findById(uuid))
                .thenReturn(Optional.of(owner));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), owner))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.createTransaction(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided category not found",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateTransaction_PaymentMethodNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        Transaction.Create payload = new Transaction.Create();
        payload.setOwner(uuid);
        payload.setCategoryId(category.getId());
        payload.setPaymentMethodId(1L);

        when(userRepository.findById(uuid))
                .thenReturn(Optional.of(owner));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), owner))
                .thenReturn(Optional.of(category));
        when(paymentMethodRepository.findByIdAndOwner(payload.getPaymentMethodId(), owner))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.createTransaction(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided payment-method not found",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateTransaction_PayloadOwnerNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        Transaction.Create payload = new Transaction.Create();
        payload.setOwner(uuid);

        when(userRepository.findById(payload.getOwner()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.createTransaction(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided owner not found",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateTransaction_WrongSessionUser() throws JsonProcessingException {
        session.setAttribute("user", objectMapper.writeValueAsString(new User(UUID.randomUUID())));

        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);

        Transaction.Create payload = new Transaction.Create();
        payload.setOwner(uuid);

        when(userRepository.findById(payload.getOwner()))
                .thenReturn(Optional.of(owner));

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.createTransaction(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Your transaction owner has to be your session-user",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateTransaction_Success() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(1L);
        paymentMethod.setOwner(owner);

        Transaction.Create payload = new Transaction.Create();
        payload.setOwner(uuid);
        payload.setCategoryId(category.getId());
        payload.setPaymentMethodId(paymentMethod.getId());

        Transaction transaction = new Transaction();
        transaction.setId(1L);
        transaction.setOwner(owner);
        transaction.setCategory(category);
        transaction.setPaymentMethod(paymentMethod);

        when(userRepository.findById(payload.getOwner()))
                .thenReturn(Optional.of(owner));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), owner))
                .thenReturn(Optional.of(category));
        when(paymentMethodRepository.findByIdAndOwner(payload.getPaymentMethodId(), owner))
                .thenReturn(Optional.of(paymentMethod));
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(transaction);

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.createTransaction(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(transaction, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetTransaction_InvalidSession() throws Exception {
        session.invalidate();
        mockMvc
                .perform(MockMvcRequestBuilders
                        .get("/v1/transaction")
                        .session(session)
                )
                .andExpect(MockMvcResultMatchers.status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testGetTransaction_UserNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        List<Transaction> transactionList = new ArrayList<>();
        when(userRepository.findById(uuid))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController.getTransactionsByUuid(uuid, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided user doesn't exist",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(transactionList, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetTransaction_WrongSessionUser() throws JsonProcessingException {
        session.setAttribute("user", objectMapper.writeValueAsString(new User(UUID.randomUUID())));

        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        List<Transaction> transactionList = new ArrayList<>();

        when(userRepository.findById(uuid)).thenReturn(Optional.of(owner));

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController.getTransactionsByUuid(uuid, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't retrieve transactions of different users",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(transactionList, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetTransaction_Success() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        List<Transaction> transactionList = new ArrayList<>();

        when(userRepository.findById(uuid))
                .thenReturn(Optional.of(owner));
        when(transactionRepository.findAllByOwner(owner))
                .thenReturn(transactionList);

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController.getTransactionsByUuid(uuid, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(transactionList, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateTransaction_InvalidSession() throws Exception {
        session.invalidate();
        mockMvc
                .perform(MockMvcRequestBuilders
                        .put("/v1/transaction")
                        .session(session)
                )
                .andExpect(MockMvcResultMatchers.status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testUpdateTransaction_TransactionNotFound() throws JsonProcessingException {
        Transaction.Update payload = new Transaction.Update();
        payload.setTransactionId(1L);

        when(transactionRepository.findById(payload.getTransactionId()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.updateTransaction(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided transaction not found", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateTransaction_CategoryNotFound() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Transaction transaction = new Transaction();
        transaction.setId(1L);
        transaction.setOwner(owner);

        Transaction.Update payload = new Transaction.Update();
        payload.setTransactionId(1L);
        payload.setCategoryId(1L);

        when(transactionRepository.findById(payload.getTransactionId()))
                .thenReturn(Optional.of(transaction));
        when(categoryRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.updateTransaction(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided category not found", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateTransaction_PaymentMethodNotFound() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Transaction transaction = new Transaction();
        transaction.setId(1L);
        transaction.setOwner(owner);

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        Transaction.Update payload = new Transaction.Update();
        payload.setTransactionId(1L);
        payload.setCategoryId(1L);

        when(transactionRepository.findById(payload.getTransactionId()))
                .thenReturn(Optional.of(transaction));
        when(categoryRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.of(category));
        when(paymentMethodRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.updateTransaction(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided payment-method not found", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateTransaction_WrongSessionUser() throws JsonProcessingException {
        session.setAttribute("user", objectMapper.writeValueAsString(new User(UUID.randomUUID())));

        User owner = new User(UUID.randomUUID());

        Transaction transaction = new Transaction();
        transaction.setId(1L);
        transaction.setOwner(owner);

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(1L);
        paymentMethod.setOwner(owner);

        Transaction.Update payload = new Transaction.Update();
        payload.setTransactionId(1L);
        payload.setCategoryId(1L);
        payload.setPaymentMethodId(1L);

        when(transactionRepository.findById(payload.getTransactionId()))
                .thenReturn(Optional.of(transaction));
        when(categoryRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.of(category));
        when(paymentMethodRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.of(paymentMethod));

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.updateTransaction(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You don't own this transaction",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateTransaction_Success() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Transaction transaction = new Transaction();
        transaction.setId(1L);
        transaction.setOwner(owner);
        transaction.setDescription("Not paid yet");

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(1L);
        paymentMethod.setOwner(owner);

        Transaction.Update payload = new Transaction.Update();
        payload.setTransactionId(1L);
        payload.setCategoryId(1L);
        payload.setPaymentMethodId(1L);
        payload.setDescription(null);

        Transaction updatedTransaction = new Transaction();
        updatedTransaction.setId(1L);
        updatedTransaction.setOwner(owner);
        updatedTransaction.setDescription(null);

        when(transactionRepository.findById(payload.getTransactionId()))
                .thenReturn(Optional.of(transaction));
        when(categoryRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.of(category));
        when(paymentMethodRepository.findByIdAndOwner(1L, owner))
                .thenReturn(Optional.of(paymentMethod));
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(updatedTransaction);

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.updateTransaction(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(updatedTransaction, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteTransaction_InvalidSession() throws Exception {
        session.invalidate();
        mockMvc
                .perform(MockMvcRequestBuilders
                        .delete("/v1/transaction")
                        .session(session)
                )
                .andExpect(MockMvcResultMatchers.status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void testDeleteTransaction_TransactionNotFound() throws JsonProcessingException {
        Transaction.Delete payload = new Transaction.Delete();
        payload.setTransactionId(1L);

        when(transactionRepository.findById(payload.getTransactionId()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.deleteTransaction(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided transaction not found", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteTransaction_WrongSessionUser() throws JsonProcessingException {
        session.setAttribute("user", objectMapper.writeValueAsString(new User(UUID.randomUUID())));

        User owner = new User(UUID.randomUUID());

        Transaction transaction = new Transaction();
        transaction.setId(1L);
        transaction.setOwner(owner);

        Transaction.Delete payload = new Transaction.Delete();
        payload.setTransactionId(1L);

        when(transactionRepository.findById(payload.getTransactionId()))
                .thenReturn(Optional.of(transaction));

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.deleteTransaction(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You don't own this transaction",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteTransaction_Success() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Transaction transaction = new Transaction();
        transaction.setId(1L);
        transaction.setOwner(owner);

        Transaction.Delete payload = new Transaction.Delete();
        payload.setTransactionId(1L);

        when(transactionRepository.findById(payload.getTransactionId()))
                .thenReturn(Optional.of(transaction));

        ResponseEntity<ApiResponse<Transaction>> response = transactionController.deleteTransaction(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(transaction, Objects.requireNonNull(response.getBody()).getData());
    }
}