package de.budgetbuddy.backend.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.auth.AuthorizationInterceptor;
import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.category.CategoryRepository;
import de.budgetbuddy.backend.paymentMethod.PaymentMethod;
import de.budgetbuddy.backend.paymentMethod.PaymentMethodRepository;
import de.budgetbuddy.backend.subscription.SubscriptionRepository;
import de.budgetbuddy.backend.transaction.file.TransactionFile;
import de.budgetbuddy.backend.transaction.file.TransactionFileRepository;
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
    private final TransactionFileRepository transactionFileRepository;
    private final TransactionService transactionService;

    public TransactionController(UserRepository userRepository,
                                 CategoryRepository categoryRepository,
                                 PaymentMethodRepository paymentMethodRepository,
                                 SubscriptionRepository subscriptionRepository,
                                 TransactionRepository transactionRepository,
                                 TransactionFileRepository transactionFileRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
        this.transactionFileRepository = transactionFileRepository;
        this.transactionService = new TransactionService(transactionRepository);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<Transaction>>> createTransaction(
            @RequestBody List<Transaction.Create> payload,
            HttpSession session) throws JsonProcessingException {
        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        }

        List<Transaction> transactions = new ArrayList<>();
        User sessionUser = optSessionUser.get();
        for (Transaction.Create transactionAttrs : payload) {
            UUID transactionOwnerUuid = transactionAttrs.getOwner();
            Optional<User> optTransactionOwner = userRepository.findById(transactionOwnerUuid);

            if (optTransactionOwner.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided owner not found"));
            }

            User transactionOwner = optTransactionOwner.get();

            if (!sessionUser.getUuid().equals(transactionOwner.getUuid())
                    && !sessionUser.getRole().isGreaterOrEqualThan(RolePermission.SERVICE_ACCOUNT)) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ApiResponse<>(
                                HttpStatus.CONFLICT.value(),
                                "You don't have the permissions to create transactions for a different user"));
            }

            Optional<Category> optCategory = categoryRepository
                    .findByIdAndOwner(transactionAttrs.getCategoryId(), transactionOwner);
            if (optCategory.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(
                                HttpStatus.NOT_FOUND.value(),
                                "Provided category not found"));
            }

            Optional<PaymentMethod> optPaymentMethod = paymentMethodRepository
                    .findByIdAndOwner(transactionAttrs.getPaymentMethodId(), transactionOwner);
            if (optPaymentMethod.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(
                                HttpStatus.NOT_FOUND.value(),
                                "Provided payment-method not found"));
            }

            transactions.add(Transaction.builder()
                    .owner(transactionOwner)
                    .category(optCategory.get())
                    .paymentMethod(optPaymentMethod.get())
                    .processedAt(transactionAttrs.getProcessedAt())
                    .receiver(transactionAttrs.getReceiver())
                    .description(transactionAttrs.getDescription())
                    .transferAmount(transactionAttrs.getTransferAmount())
                    .attachedFiles(new ArrayList<>())
                    .createdAt(new Date())
                    .build());
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(transactionRepository.saveAll(transactions)));
    }

    @GetMapping("/single")
    public ResponseEntity<ApiResponse<Transaction>> getTransactionById(
            @RequestParam Long transactionId,
            HttpSession session) throws JsonProcessingException {
        Optional<Transaction> optTransaction = transactionRepository
                .findByIdAndOwner(transactionId, AuthorizationInterceptor.getSessionUser(session).orElseThrow());
        return optTransaction.map(transaction -> ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(transaction))).orElseGet(() -> ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided transaction not found")));

    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Transaction>>> getTransactionsByUuid(@RequestParam UUID uuid,
                                                                                HttpSession session) throws JsonProcessingException {
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

        List<Transaction> transactions = transactionRepository.findTransactionsByOwnerOrderByProcessedAtDesc(user.get());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(transactions));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Transaction>> updateTransaction(@RequestBody Transaction.Update payload,
                                                                      HttpSession session) throws JsonProcessingException {
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
                    .body(new ApiResponse<>(
                            HttpStatus.CONFLICT.value(),
                            "You don't own this transaction"));
        }

        Optional<Category> optCategory = categoryRepository
                .findByIdAndOwner(payload.getCategoryId(), transactionOwner);
        if (optCategory.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(
                            HttpStatus.NOT_FOUND.value(),
                            "Provided category not found"));
        }

        Optional<PaymentMethod> optPaymentMethod = paymentMethodRepository
                .findByIdAndOwner(payload.getPaymentMethodId(), transactionOwner);
        if (optPaymentMethod.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(
                            HttpStatus.NOT_FOUND.value(),
                            "Provided payment-method not found"));
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(transactionRepository.save(Transaction.builder()
                        .id(transaction.getId())
                        .owner(transactionOwner)
                        .category(optCategory.get())
                        .paymentMethod(optPaymentMethod.get())
                        .processedAt(payload.getProcessedAt())
                        .receiver(payload.getReceiver())
                        .description(payload.getDescription())
                        .transferAmount(payload.getTransferAmount())
                        .attachedFiles(transaction.getAttachedFiles())
                        .createdAt(transaction.getCreatedAt())
                        .build())));
    }

    @PostMapping("/file")
    public ResponseEntity<ApiResponse<List<TransactionFile>>> attachFiles(
            @RequestBody List<TransactionFile.Create> files,
            HttpSession session) throws JsonProcessingException {
        if (files.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "No files provided"));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        }

        List<TransactionFile> transactionFiles = files.stream()
//                .filter(file -> transactionRepository.findById(file.getTransactionId()).isPresent())
                .map(file -> {
                    Optional<Transaction> optTransaction = transactionRepository.findById(file.getTransactionId());
                    if (optTransaction.isEmpty()) return null;
                    Transaction transaction = optTransaction.get();
                    User transactionOwner = transaction.getOwner();

                    if (!transactionOwner.getUuid().equals(sessionUser.get().getUuid())) {
                        return null;
                    }

                    return TransactionFile.builder()
                            .owner(transactionOwner)
                            .transaction(transaction)
                            .fileName(file.getFileName())
                            .fileSize(file.getFileSize())
                            .mimeType(file.getMimeType())
                            .location(file.getFileUrl())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        if (transactionFiles.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "No valid transactions and files we're provided"));
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(HttpStatus.OK.value(), transactionFileRepository.saveAll(transactionFiles)));
    }

    @DeleteMapping("/file")
    public ResponseEntity<ApiResponse<List<TransactionFile>>> detachFiles(
            @RequestBody List<TransactionFile.Delete> files,
            HttpSession session) throws JsonProcessingException {
        if (files.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "No file id's provided"));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        }

        List<TransactionFile> transactionFiles = files.stream()
//                .filter(file -> transactionFileRepository.findById(file.getUuid()).isPresent())
                .map(file -> {
                    Optional<TransactionFile> optionalTransactionFile = transactionFileRepository.findById(file.getUuid());
                    if (optionalTransactionFile.isEmpty()) return null;
                    TransactionFile transactionFile = optionalTransactionFile.get();
                    User transactionFileOwner = transactionFile.getOwner();
                    if (!transactionFileOwner.getUuid().equals(sessionUser.get().getUuid())) {
                        return null;
                    }
                    return transactionFile;
                })
                .filter(Objects::nonNull)
                .toList();

        if (transactionFiles.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "No valid file id's we're provided"));
        }

        transactionFileRepository.deleteAllById(transactionFiles.stream().map(TransactionFile::getUuid).toList());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(HttpStatus.OK.value(), transactionFiles));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, List<?>>>> deleteTransactions(
            @RequestBody List<Transaction.Delete> payloads,
            HttpSession session) throws JsonProcessingException {
        if (payloads.size() == 0) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(HttpStatus.BAD_REQUEST, "No transactions we're provided"));
        }

        List<Transaction> successfullyDeleted = new ArrayList<>();
        List<Transaction.Delete> failedToDelete = new ArrayList<>();

        for (Transaction.Delete payload : payloads) {
            Optional<Transaction> optTransaction = transactionRepository.findById(payload.getTransactionId());
            if (optTransaction.isEmpty()) {
                failedToDelete.add(payload);
            } else {
                Transaction transaction = optTransaction.get();
                User transactionOwner = transaction.getOwner();
                Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);

                if (optSessionUser.isEmpty()
                        || !optSessionUser.get().getUuid().equals(transactionOwner.getUuid())) {
                    failedToDelete.add(payload);
                } else {
                    transactionRepository.delete(transaction);
                    successfullyDeleted.add(transaction);
                }
            }
        }

        Map<String, List<?>> response = new HashMap<>();
        response.put("success", successfullyDeleted);
        response.put("failed", failedToDelete);
        boolean didAllFail = failedToDelete.size() == payloads.size();
        return ResponseEntity
                .status(didAllFail ? HttpStatus.BAD_REQUEST : HttpStatus.OK)
                .body(new ApiResponse<>(
                        didAllFail ? HttpStatus.BAD_REQUEST : HttpStatus.OK,
                        didAllFail ? "All provided transactions we're invalid values" : null,
                        response));
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
        LocalDate tomorrow = today.plusDays(1);
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate lastDayOfMonth = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

        Double receivedEarnings = transactionRepository
                .getTransactionSumByDateRange(firstDayOfMonth, lastDayOfMonth, sessionUserUUID, DailyTransactionType.INCOME.toString());
        Double paidExpenses = transactionRepository
                .getTransactionSumByDateRange(firstDayOfMonth, lastDayOfMonth, sessionUserUUID, DailyTransactionType.SPENDINGS.toString());
        Double balance = transactionRepository.getBalance(firstDayOfMonth, lastDayOfMonth, sessionUserUUID);

        Double upcomingSubscriptionEarnings = subscriptionRepository.getUpcomingSubscriptioEarnings(sessionUserUUID);
        Double upcomingTransactionEarnings = transactionRepository
                .getTransactionSumByDateRange(tomorrow, lastDayOfMonth, sessionUserUUID, DailyTransactionType.INCOME.toString());
        Double upcomingSubscriptioExpenses = subscriptionRepository.getUpcomingSubscriptionExpenses(sessionUserUUID);
        Double upcomingTransactionExpenses = transactionRepository
                .getTransactionSumByDateRange(tomorrow, lastDayOfMonth, sessionUserUUID, DailyTransactionType.SPENDINGS.toString());

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

    @GetMapping("/monthly-balance")
    public ResponseEntity<ApiResponse<List<MonthlyBalance>>> getMonthlyBalance(HttpSession session) throws JsonProcessingException {
        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        }

        return ResponseEntity
                .status(200)
                .body(new ApiResponse<>(transactionRepository.getMonthlyBalance(optSessionUser.get())));
    }
}
