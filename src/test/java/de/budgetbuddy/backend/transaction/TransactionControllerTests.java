package de.budgetbuddy.backend.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.category.CategoryRepository;
import de.budgetbuddy.backend.paymentMethod.PaymentMethod;
import de.budgetbuddy.backend.paymentMethod.PaymentMethodRepository;
import de.budgetbuddy.backend.subscription.SubscriptionRepository;
import de.budgetbuddy.backend.transaction.file.TransactionFile;
import de.budgetbuddy.backend.transaction.file.TransactionFileRepository;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import de.budgetbuddy.backend.user.role.Role;
import de.budgetbuddy.backend.user.role.RolePermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
public class TransactionControllerTests {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionFileRepository transactionFileRepository;
    private final TransactionService transactionService;
    private final TransactionController transactionController;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockHttpSession session;

    TransactionControllerTests() {
        this.userRepository = Mockito.mock(UserRepository.class);
        this.categoryRepository = Mockito.mock(CategoryRepository.class);
        this.paymentMethodRepository = Mockito.mock(PaymentMethodRepository.class);
        SubscriptionRepository subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
        this.transactionRepository = Mockito.mock(TransactionRepository.class);
        this.transactionFileRepository = Mockito.mock(TransactionFileRepository.class);
        this.transactionController = new TransactionController(
                userRepository,
                categoryRepository,
                paymentMethodRepository,
                subscriptionRepository,
                transactionRepository,
                transactionFileRepository);
        this.transactionService = new TransactionService(transactionRepository);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        session = new MockHttpSession();
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

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController
                .createTransaction(List.of(payload), session);

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

        de.budgetbuddy.backend.category.Category category = new de.budgetbuddy.backend.category.Category();
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

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController
                .createTransaction(List.of(payload), session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided payment-method not found",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateTransaction_PayloadOwnerNotFound() throws JsonProcessingException {
        User sessionAdminUser = new User(UUID.randomUUID());
        sessionAdminUser.setRole(new Role(RolePermission.ADMIN));
        session.setAttribute("user", objectMapper.writeValueAsString(sessionAdminUser));

        UUID uuid = UUID.randomUUID();
        Transaction.Create payload = new Transaction.Create();
        payload.setOwner(uuid);

        when(userRepository.findById(payload.getOwner()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController
                .createTransaction(List.of(payload), session);

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

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController
                .createTransaction(List.of(payload), session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You don't have the permissions to create transactions for a different user",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateTransaction_WithSupportUserSuccess() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        sessionUser.setRole(new Role(RolePermission.SERVICE_ACCOUNT));
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));
        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);

        de.budgetbuddy.backend.category.Category category = new de.budgetbuddy.backend.category.Category();
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
        when(transactionRepository.saveAll(ArgumentMatchers.anyList()))
                .thenReturn(List.of(transaction));

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController
                .createTransaction(List.of(payload), session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(List.of(transaction), Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateTransaction_Success() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        de.budgetbuddy.backend.category.Category category = new de.budgetbuddy.backend.category.Category();
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
        when(transactionRepository.saveAll(ArgumentMatchers.anyList()))
                .thenReturn(List.of(transaction));

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController
                .createTransaction(List.of(payload), session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(List.of(transaction), Objects.requireNonNull(response.getBody()).getData());
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

        when(userRepository.findById(uuid)).thenReturn(Optional.of(owner));

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController.getTransactionsByUuid(uuid, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You don't have the permissions to retrieve transactions from a different user",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetTransaction_WithSupportUserSuccess() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        sessionUser.setRole(new Role(RolePermission.SERVICE_ACCOUNT));
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        UUID uuid = UUID.randomUUID();
        User owner = new User(uuid);

        List<Transaction> transactionList = new ArrayList<>();

        when(userRepository.findById(uuid))
                .thenReturn(Optional.of(owner));
        when(transactionRepository.findTransactionsByOwnerOrderByProcessedAtDesc(owner))
                .thenReturn(transactionList);

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController.getTransactionsByUuid(uuid, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
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
        when(transactionRepository.findTransactionsByOwnerOrderByProcessedAtDesc(owner))
                .thenReturn(transactionList);

        ResponseEntity<ApiResponse<List<Transaction>>> response = transactionController.getTransactionsByUuid(uuid, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(transactionList, Objects.requireNonNull(response.getBody()).getData());
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

        de.budgetbuddy.backend.category.Category category = new de.budgetbuddy.backend.category.Category();
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

        de.budgetbuddy.backend.category.Category category = new de.budgetbuddy.backend.category.Category();
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

        de.budgetbuddy.backend.category.Category category = new de.budgetbuddy.backend.category.Category();
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
    void testDeleteTransaction_EmptyList() throws JsonProcessingException {
        List<Transaction.Delete> payload = new ArrayList<>();

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                transactionController.deleteTransactions(payload, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No transactions we're provided",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteTransaction_AllItemsInvalid() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        List<Transaction.Delete> payload = new ArrayList<>();
        payload.add(Transaction.builder()
                .id(1L)
                .owner(owner)
                .build()
                .toDelete());


        when(transactionRepository.findById(any(Long.class)))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                transactionController.deleteTransactions(payload, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("All provided transactions we're invalid values",
                Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(0, responseBody.get("success").size());
    }

    @Test
    void testDeleteTransaction_SomeFailures() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        List<Transaction.Delete> payload = new ArrayList<>();
        Transaction t1 = Transaction.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(t1.toDelete());

        Transaction t2 = Transaction.builder()
                .id(2L)
                .build();
        payload.add(t2.toDelete());

        when(transactionRepository.findById(1L))
                .thenReturn(Optional.of(t1));
        when(transactionRepository.findById(2L))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                transactionController.deleteTransactions(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(1, responseBody.get("success").size());
    }

    @Test
    void testDeleteTransaction_WrongSessionUser() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        List<Transaction.Delete> payload = new ArrayList<>();
        Transaction t1 = Transaction.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(t1.toDelete());

        Transaction t2 = Transaction.builder()
                .id(2L)
                .owner(sessionUser)
                .build();
        payload.add(t2.toDelete());

        when(transactionRepository.findById(1L))
                .thenReturn(Optional.of(t1));
        when(transactionRepository.findById(2L))
                .thenReturn(Optional.of(t2));

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                transactionController.deleteTransactions(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(1, responseBody.get("success").size());
    }

    @Test
    void testDeleteTransaction_Success() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        List<Transaction.Delete> payload = new ArrayList<>();
        Transaction t1 = Transaction.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(t1.toDelete());

        Transaction t2 = Transaction.builder()
                .id(2L)
                .owner(owner)
                .build();
        payload.add(t2.toDelete());

        when(transactionRepository.findById(1L))
                .thenReturn(Optional.of(t1));
        when(transactionRepository.findById(2L))
                .thenReturn(Optional.of(t2));

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                transactionController.deleteTransactions(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(0, responseBody.get("failed").size());
        assertEquals(2, responseBody.get("success").size());
        assertEquals(List.of(t1, t2), responseBody.get("success"));
    }

    @Test
    void testGetDailyTransactions_InvalidDateRange() throws JsonProcessingException {
        LocalDate startDate = LocalDate.of(2023, 10, 02);
        LocalDate endDate = LocalDate.of(2023, 10, 01);
        DailyTransactionType requestedData = DailyTransactionType.SPENDINGS;
        ResponseEntity<ApiResponse<List<Transaction.DailyTransaction>>> response =
                transactionController.getDailyTransactions(startDate, endDate, requestedData, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("The startDate needs to be before the endDate",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetDailyTransactions_Success() throws JsonProcessingException {
        User user = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(user));


        LocalDate startDate = LocalDate.of(2023, 10, 1);
        LocalDate endDate = LocalDate.of(2023, 10, 7);
        DailyTransactionType requestedData = DailyTransactionType.SPENDINGS;

        when(transactionService.getDailyTransactions(startDate, endDate, requestedData, user.getUuid()))
                .thenReturn(new ArrayList<>());

        ResponseEntity<ApiResponse<List<Transaction.DailyTransaction>>> response =
                transactionController.getDailyTransactions(startDate, endDate, requestedData, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(0, Objects.requireNonNull(response.getBody()).getData().size());
    }

    @Test
    void attachFiles_NoFilesProvided() throws JsonProcessingException {
        List<TransactionFile.Create> files = new ArrayList<>();

        ResponseEntity<ApiResponse<List<TransactionFile>>> response =
                transactionController.attachFiles(files, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No files provided", Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void attachFiles_OnlyInvalidFilesProvided() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        TransactionFile.Create file = TransactionFile.Create.builder()
                .transactionId(1L)
                .build();

        List<TransactionFile.Create> files = List.of(file);

        when(transactionRepository.findById(file.getTransactionId()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<List<TransactionFile>>> response =
                transactionController.attachFiles(files, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No valid transactions and files we're provided",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void attachFiles_SomeInvalidFilesProvided() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        Transaction t2  = Transaction.builder()
                .id(2L)
                .owner(new User(UUID.randomUUID()))
                .build();

        Transaction t3 = Transaction.builder()
                .id(3L)
                .owner(sessionUser)
                .build();

        List<TransactionFile.Create> files = List.of(
                TransactionFile.Create.builder()
                        .transactionId(1L)
                        .build(),
                TransactionFile.Create.builder()
                        .transactionId(t2.getId())
                        .build(),
                TransactionFile.Create.builder()
                        .transactionId(t3.getId())
                        .build()
        );

        List<TransactionFile> transactionFiles = List.of(TransactionFile.builder()
                .transaction(t3)
                .build());

        when(transactionRepository.findById(1L))
                .thenReturn(Optional.empty());

        when(transactionRepository.findById(t2.getId()))
                .thenReturn(Optional.of(t2));

        when(transactionRepository.findById(t3.getId()))
                .thenReturn(Optional.of(t3));

        when(transactionFileRepository.saveAll(any()))
                .thenReturn(transactionFiles);

        ResponseEntity<ApiResponse<List<TransactionFile>>> response =
                transactionController.attachFiles(files, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(transactionFiles, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void attachFiles_Success() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        Transaction transaction = Transaction.builder()
                .id(1L)
                .owner(sessionUser)
                .build();

        List<TransactionFile.Create> files = List.of(
                TransactionFile.Create.builder()
                        .transactionId(transaction.getId())
                        .build()
        );

        List<TransactionFile> transactionFiles = List.of(TransactionFile.builder()
                .transaction(transaction)
                .owner(sessionUser)
                .build());

        when(transactionRepository.findById(transaction.getId()))
                .thenReturn(Optional.of(transaction));

        when(transactionFileRepository.saveAll(any()))
                .thenReturn(transactionFiles);

        ResponseEntity<ApiResponse<List<TransactionFile>>> response =
                transactionController.attachFiles(files, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(transactionFiles, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void detachFiles_NoFilesProvided() throws JsonProcessingException {
        ResponseEntity<ApiResponse<List<TransactionFile>>> response =
                transactionController.detachFiles(new ArrayList<>(), session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No file id's provided", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void detachFiles_OnlyInvalidFilesProvided() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        when(transactionFileRepository.findById(any(UUID.class)))
                .thenReturn(Optional.empty());

        List<TransactionFile.Delete> fileIds = List.of(
                TransactionFile.Delete.builder().uuid(UUID.randomUUID()).build()
        );

        ResponseEntity<ApiResponse<List<TransactionFile>>> response =
                transactionController.detachFiles(fileIds, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No valid file id's we're provided", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void detachFiles_SomeInvalidFilesProvided() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        Transaction transaction = Transaction.builder()
                .id(1L)
                .owner(sessionUser)
                .build();

        when(transactionRepository.findById(transaction.getId()))
                .thenReturn(Optional.of(transaction));

        TransactionFile invalidFile = TransactionFile.builder()
                .uuid(UUID.randomUUID())
                .transaction(any(Transaction.class))
                .owner(new User(UUID.randomUUID()))
                .build();

        TransactionFile validFile = TransactionFile.builder()
                .uuid(UUID.randomUUID())
                .transaction(transaction)
                .owner(sessionUser)
                .build();

        List<TransactionFile.Delete> fileIds = Stream.of(invalidFile.getUuid(), validFile.getUuid())
                .map(uuid -> TransactionFile.Delete.builder().uuid(uuid).build())
                .toList();

        when(transactionFileRepository.findById(invalidFile.getUuid()))
                .thenReturn(Optional.of(invalidFile));

        when(transactionFileRepository.findById(validFile.getUuid()))
                .thenReturn(Optional.of(validFile));

        ResponseEntity<ApiResponse<List<TransactionFile>>> response =
                transactionController.detachFiles(fileIds, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(List.of(validFile), Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void detachFiles_Success() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        Transaction transaction = Transaction.builder()
                .id(1L)
                .owner(sessionUser)
                .build();

        when(transactionRepository.findById(transaction.getId()))
                .thenReturn(Optional.of(transaction));

        TransactionFile validFile = TransactionFile.builder()
                .uuid(UUID.randomUUID())
                .transaction(transaction)
                .owner(sessionUser)
                .build();

        TransactionFile anotherValidFile = TransactionFile.builder()
                .uuid(UUID.randomUUID())
                .transaction(transaction)
                .owner(sessionUser)
                .build();

        List<TransactionFile.Delete> fileIds = Stream.of(validFile.getUuid(), anotherValidFile.getUuid())
                .map(uuid -> TransactionFile.Delete.builder().uuid(uuid).build())
                .toList();

        when(transactionFileRepository.findById(validFile.getUuid()))
                .thenReturn(Optional.of(validFile));

        when(transactionFileRepository.findById(anotherValidFile.getUuid()))
                .thenReturn(Optional.of(anotherValidFile));

        ResponseEntity<ApiResponse<List<TransactionFile>>> response =
                transactionController.detachFiles(fileIds, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(List.of(validFile, anotherValidFile), Objects.requireNonNull(response.getBody()).getData());
    }
}
