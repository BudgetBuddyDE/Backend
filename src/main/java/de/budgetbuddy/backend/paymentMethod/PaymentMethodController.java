package de.budgetbuddy.backend.paymentMethod;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.auth.AuthorizationInterceptor;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/payment-method")
public class PaymentMethodController {
    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;

    @Autowired
    public PaymentMethodController(PaymentMethodRepository paymentMethodRepository, UserRepository userRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentMethod>> createPaymentMethod(@RequestBody PaymentMethod.Create payload, HttpSession session) throws JsonProcessingException {
        Optional<User> user = userRepository.findById(payload.getOwner());
        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided user doesn't exist"));
        }

        if (paymentMethodRepository.findByOwnerAndNameAndAddress(user.get(), payload.getName(), payload.getAddress()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "There is already an payment-method by this name and address"));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(payload.getOwner())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't create payment-methods for other users"));
        }

        PaymentMethod paymentMethod = new PaymentMethod(user.get(), payload.getName(), payload.getAddress(), payload.getDescription());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(paymentMethodRepository.save(paymentMethod)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentMethod>>> getPaymentMethodsByUuid(@RequestParam UUID uuid) {
        Optional<User> user = userRepository.findById(uuid);
        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided user doesn't exist", new ArrayList<>()));
        }

        List<PaymentMethod> paymentMethods = paymentMethodRepository.findAllByOwner(user.get());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(paymentMethods));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<PaymentMethod>> updatePaymentMethod(@RequestBody PaymentMethod.Update payload, HttpSession session) throws JsonProcessingException {
        Optional<PaymentMethod> requestedPaymentMethod = paymentMethodRepository.findById(payload.getId());
        if (requestedPaymentMethod.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided payment-method doesn't exist"));
        }

        PaymentMethod paymentMethod = requestedPaymentMethod.get();
        if (paymentMethodRepository
                .findByOwnerAndNameAndAddress(requestedPaymentMethod.get().getOwner(), payload.getName(), payload.getAddress())
                .isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "There is already an payment-method by this name and address"));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(paymentMethod.getOwner().getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't modify payment-methods from other users"));
        }

        PaymentMethod updatedPaymentMethod = new PaymentMethod(paymentMethod.getId(), paymentMethod.getOwner(), payload.getName(), payload.getAddress(), payload.getDescription(), paymentMethod.getCreatedAt());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(paymentMethodRepository.save(updatedPaymentMethod)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<PaymentMethod>> deletePaymentMethod(@RequestBody PaymentMethod.Delete payload, HttpSession session) throws JsonProcessingException {
        Optional<PaymentMethod> paymentMethod =  paymentMethodRepository.findById(payload.getPaymentMethodId());
        if (paymentMethod.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided payment-method doesn't exist"));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(paymentMethod.get().getOwner().getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't delete payment-methods from other users"));
        }

        paymentMethodRepository.deleteById(payload.getPaymentMethodId());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(200, paymentMethod.get()));
    }

}
