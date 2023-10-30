package de.budgetbuddy.backend.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.auth.AuthorizationInterceptor;
import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.category.CategoryRepository;
import de.budgetbuddy.backend.paymentMethod.PaymentMethod;
import de.budgetbuddy.backend.paymentMethod.PaymentMethodRepository;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/transaction")
public class TransactionController {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final TransactionRepository transactionRepository;

    public TransactionController(UserRepository userRepository, CategoryRepository categoryRepository, PaymentMethodRepository paymentMethodRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.transactionRepository = transactionRepository;
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
        } else if (!optSessionUser.get().getUuid().equals(transactionOwner.getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "Your transaction owner has to be your session-user"));
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
        } else if (!optSessionUser.get().getUuid().equals(uuid)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't retrieve transactions of different users", new ArrayList<>()));
        }

        List<Transaction> transactions = transactionRepository.findAllByOwner(user.get());
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
}
