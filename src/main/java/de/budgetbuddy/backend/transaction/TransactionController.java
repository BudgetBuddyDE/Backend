package de.budgetbuddy.backend.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.auth.AuthorizationInterceptor;
import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.category.CategoryRepository;
import de.budgetbuddy.backend.paymentMethod.PaymentMethod;
import de.budgetbuddy.backend.paymentMethod.PaymentMethodRepository;
import de.budgetbuddy.backend.subscription.SubscriptionRepository;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import de.budgetbuddy.backend.user.role.RolePermission;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@RestController
@RequestMapping("/v1/transaction")
public class TransactionController {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    public TransactionController(UserRepository userRepository, CategoryRepository categoryRepository, PaymentMethodRepository paymentMethodRepository, SubscriptionRepository subscriptionRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
        this.transactionService = new TransactionService(transactionRepository);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Transaction>> createTransaction(@RequestBody Transaction.Create payload, HttpSession session) throws JsonProcessingException {
        UUID transactionOwnerUuid = payload.getOwner();
        Optional<User> optTransactionOwner = userRepository.findById(transactionOwnerUuid);
        if (optTransactionOwner.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided owner not found"));
        }

        User transactionOwner = optTransactionOwner.get();
        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else {
            User sessionUser = optSessionUser.get();
            if (!sessionUser.getUuid().equals(transactionOwner.getUuid())
                    && !sessionUser.getRole().isGreaterOrEqualThan(RolePermission.SERVICE_ACCOUNT)) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You don't have the permissions to create transactions for a different user"));

            }
        }

        Optional<Category> optCategory = categoryRepository.findByIdAndOwner(payload.getCategoryId(), transactionOwner);
        if (optCategory.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided category not found"));
        }

        Optional<PaymentMethod> optPaymentMethod = paymentMethodRepository.findByIdAndOwner(payload.getPaymentMethodId(), transactionOwner);
        if (optPaymentMethod.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided payment-method not found"));
        }

        Transaction transaction = new Transaction(
                transactionOwner,
                optCategory.get(),
                optPaymentMethod.get(),
                payload.getProcessedAt(),
                payload.getReceiver(),
                payload.getDescription(),
                payload.getTransferAmount()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(transactionRepository.save(transaction)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Transaction>>> getTransactionsByUuid(@RequestParam UUID uuid, HttpSession session) throws JsonProcessingException {
        Optional<User> user = userRepository.findById(uuid);
        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided user doesn't exist", new ArrayList<>()));
        }

        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else {
            User sessionUser = optSessionUser.get();
            if (!sessionUser.getUuid().equals(uuid)
                    && !sessionUser.getRole().isGreaterOrEqualThan(RolePermission.SERVICE_ACCOUNT)) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You don't have the permissions to retrieve transactions from a different user"));

            }
        }

        List<Transaction> transactions = transactionRepository.findAllByOwnerOrderByProcessedAtDesc(user.get());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(transactions));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Transaction>> updateTransaction(@RequestBody Transaction.Update payload, HttpSession session) throws JsonProcessingException {
        Optional<Transaction> optTransaction = transactionRepository.findById(payload.getTransactionId());
        if (optTransaction.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided transaction not found"));
        }

        Transaction transaction = optTransaction.get();
        User transactionOwner  = transaction.getOwner();
        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!optSessionUser.get().getUuid().equals(transactionOwner.getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You don't own this transaction"));
        }

        Optional<Category> optCategory = categoryRepository.findByIdAndOwner(payload.getCategoryId(), transactionOwner);
        if (optCategory.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided category not found"));
        }

        Optional<PaymentMethod> optPaymentMethod = paymentMethodRepository.findByIdAndOwner(payload.getPaymentMethodId(), transactionOwner);
        if (optPaymentMethod.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided payment-method not found"));
        }

        Transaction updatedTransaction = new Transaction(
                transaction.getId(),
                transactionOwner,
                optCategory.get(),
                optPaymentMethod.get(),
                payload.getProcessedAt(),
                payload.getReceiver(),
                payload.getDescription(),
                payload.getTransferAmount(),
                transaction.getCreatedAt()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(transactionRepository.save(updatedTransaction)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Transaction>> deleteTransaction(@RequestBody Transaction.Delete payload, HttpSession session) throws JsonProcessingException {
        Optional<Transaction> optTransaction = transactionRepository.findById(payload.getTransactionId());
        if (optTransaction.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided transaction not found"));
        }

        Transaction transaction = optTransaction.get();
        User transactionOwner = transaction.getOwner();
        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!optSessionUser.get().getUuid().equals(transactionOwner.getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You don't own this transaction"));
        }

        transactionRepository.delete(transaction);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(transaction));
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<List<Transaction.DailyTransaction>>> getDailyTransactions(
       @RequestParam LocalDate startDate,
       @RequestParam LocalDate endDate,
       @RequestParam DailyTransactionType requestedData,
       HttpSession session
    ) throws JsonProcessingException {
        if (startDate.isAfter(endDate)) {
            return ResponseEntity
                    .status(400)
                    .body(new ApiResponse<>(400, "The startDate needs to be before the endDate"));
        }

        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        }

        return ResponseEntity
                .status(200)
                .body(new ApiResponse<>(transactionService.getDailyTransactions(
                        startDate,
                        endDate,
                        requestedData,
                        optSessionUser.get().getUuid())));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStats>> getDashboardStats(HttpSession session) throws JsonProcessingException {
        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        }
        User sessionUser = optSessionUser.get();
        UUID sessionUserUUID = sessionUser.getUuid();

        LocalDate today = LocalDate.now();
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate lastDayOfMonth = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

        Double receivedEarnings = transactionRepository.getReceivedEarnings(firstDayOfMonth, lastDayOfMonth, sessionUserUUID);
        Double paidExpenses = transactionRepository.getPaidExpenses(firstDayOfMonth, lastDayOfMonth, sessionUserUUID);
        Double balance = transactionRepository.getBalance(firstDayOfMonth, lastDayOfMonth, sessionUserUUID);

        Double upcomingSubscriptionEarnings = subscriptionRepository.getUpcomingSubscriptioEarnings(sessionUserUUID);
        Double upcomingTransactionEarnings = transactionRepository.getReceivedEarnings(today, lastDayOfMonth, sessionUserUUID);
        Double upcomingSubscriptioExpenses = subscriptionRepository.getUpcomingSubscriptionExpenses(sessionUserUUID);
        Double upcomingTransactionExpenses = transactionRepository.getPaidExpenses(today, lastDayOfMonth, sessionUserUUID);

        Double upcomingEarnings = upcomingSubscriptionEarnings + upcomingTransactionEarnings;
        Double upcomingExpenses = upcomingSubscriptioExpenses + upcomingTransactionExpenses;

        return ResponseEntity
                .status(200)
                .body(new ApiResponse<>(new DashboardStats(
                        receivedEarnings,
                        upcomingEarnings,
                        Math.abs(paidExpenses),
                        Math.abs(upcomingExpenses),
                        balance)));
    }
}
