package de.budgetbuddy.backend.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.auth.AuthorizationInterceptor;
import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.category.CategoryRepository;
import de.budgetbuddy.backend.paymentMethod.PaymentMethod;
import de.budgetbuddy.backend.paymentMethod.PaymentMethodRepository;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import de.budgetbuddy.backend.user.role.RolePermission;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/subscription")
public class SubscriptionController {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionController(UserRepository userRepository, CategoryRepository categoryRepository, PaymentMethodRepository paymentMethodRepository, SubscriptionRepository subscriptionRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Subscription>> createSubscription(@RequestBody Subscription.Create payload, HttpSession session) throws JsonProcessingException {
        if (!Subscription.isValidExecutionDate(payload.getExecuteAt())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "Execution must lay between the first and 31nd of the month"));
        }

        UUID subscriptionOwnerUuid = payload.getOwner();
        Optional<User> optSubscriptionOwner = userRepository.findById(subscriptionOwnerUuid);
        if (optSubscriptionOwner.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided owner not found"));
        }

        User subscriptionOwner = optSubscriptionOwner.get();
        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else {
            User sessionUser = optSessionUser.get();
            if (!sessionUser.getUuid().equals(subscriptionOwner.getUuid())
                    && !sessionUser.getRole().isGreaterOrEqualThan(RolePermission.SERVICE_ACCOUNT)) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You don't have the permissions to create subscriptions for a different user"));

            }
        }

        Optional<Category> optCategory = categoryRepository.findByIdAndOwner(payload.getCategoryId(), subscriptionOwner);
        if (optCategory.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided category not found"));
        }

        Optional<PaymentMethod> optPaymentMethod = paymentMethodRepository.findByIdAndOwner(payload.getPaymentMethodId(), subscriptionOwner);
        if (optPaymentMethod.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided payment-method not found"));
        }

        Subscription subscription = new Subscription(
                subscriptionOwner,
                optCategory.get(),
                optPaymentMethod.get(),
                payload.getPaused(),
                payload.getExecuteAt(),
                payload.getReceiver(),
                payload.getDescription(),
                payload.getTransferAmount()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(subscriptionRepository.save(subscription)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Subscription>>> getSubscriptionsByUuid(
            @RequestParam UUID uuid,
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
                        .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You don't have the permissions to retrieve subscriptions from a different user"));

            }
        }

        List<Subscription> subscriptions = subscriptionRepository.findAllByOwner(user.get());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(subscriptions));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Subscription>>> getSubscriptions(
            @RequestParam(name = "execute_at") int executeAt,
            @RequestParam(name = "paused") Boolean paused,
            HttpSession session
    ) throws JsonProcessingException {
        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!optSessionUser.get().getRole().isGreaterOrEqualThan(RolePermission.SERVICE_ACCOUNT)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You don't have the permissions to retrieve subscriptions"));
        }

        if (!Subscription.isValidExecutionDate(executeAt)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "Execution must lay between the first and 31nd of the month"));
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(subscriptionRepository
                        .findAllByExecuteAtAndPaused(executeAt, paused)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Subscription>> updateSubscription(@RequestBody Subscription.Update payload, HttpSession session) throws JsonProcessingException {
        if (!Subscription.isValidExecutionDate(payload.getExecuteAt())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "Execution must lay between the first and 31nd of the month"));
        }

        Optional<Subscription> optionalSubscription = subscriptionRepository.findById(payload.getSubscriptionId());
        if (optionalSubscription.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided subscription not found"));
        }

        Subscription subscription = optionalSubscription.get();
        User subscriptionOwner  = subscription.getOwner();
        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!optSessionUser.get().getUuid().equals(subscriptionOwner.getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You don't own this subscription"));
        }

        Optional<Category> optCategory = categoryRepository
                .findByIdAndOwner(payload.getCategoryId(), subscriptionOwner);
        if (optCategory.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided category not found"));
        }

        Optional<PaymentMethod> optPaymentMethod = paymentMethodRepository
                .findByIdAndOwner(payload.getPaymentMethodId(), subscriptionOwner);
        if (optPaymentMethod.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided payment-method not found"));
        }

        Subscription updatedSubscription = new Subscription(
                subscription.getId(),
                subscriptionOwner,
                optCategory.get(),
                optPaymentMethod.get(),
                payload.getPaused(),
                payload.getExecuteAt(),
                payload.getReceiver(),
                payload.getDescription(),
                payload.getTransferAmount(),
                subscription.getCreatedAt()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(subscriptionRepository.save(updatedSubscription)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Subscription>> deleteSubscription(@RequestBody Subscription.Delete payload, HttpSession session) throws JsonProcessingException {
        Optional<Subscription> optSubscription = subscriptionRepository.findById(payload.getSubscriptionId());
        if (optSubscription.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), "Provided subscription not found"));
        }

        Subscription subscription = optSubscription.get();
        User subscriptionOwner = subscription.getOwner();
        Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (optSessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!optSessionUser.get().getUuid().equals(subscriptionOwner.getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You don't own this subscription"));
        }

        subscriptionRepository.delete(subscription);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(subscription));
    }
}
