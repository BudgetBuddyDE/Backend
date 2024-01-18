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

import java.util.*;

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

        PaymentMethod paymentMethod = new PaymentMethod(user.get(), payload.getName(), payload.getAddress(), payload.getProvider(), payload.getDescription());
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
        Optional<PaymentMethod> existingPaymentMethod = paymentMethodRepository
                .findByOwnerAndNameAndAddress(requestedPaymentMethod.get().getOwner(), payload.getName(), payload.getAddress());
        if (existingPaymentMethod.isPresent()
                && !existingPaymentMethod.get().getId().equals(payload.getId())) {
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

        PaymentMethod updatedPaymentMethod = new PaymentMethod(paymentMethod.getId(), paymentMethod.getOwner(), payload.getName(), payload.getAddress(), payload.getProvider(), payload.getDescription(), paymentMethod.getCreatedAt());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(paymentMethodRepository.save(updatedPaymentMethod)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, List<?>>>> deletePaymentMethods(
            @RequestBody List<PaymentMethod.Delete> payloads,
            HttpSession session) throws JsonProcessingException {
        if (payloads.size() == 0) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(HttpStatus.BAD_REQUEST, "No payment-methods we're provided"));
        }

        List<PaymentMethod> successfullyDeleted = new ArrayList<>();
        List<PaymentMethod.Delete> failedToDelete = new ArrayList<>();

        for (PaymentMethod.Delete payload : payloads) {
            Optional<PaymentMethod> optionalPaymentMethod = paymentMethodRepository.findById(payload.getPaymentMethodId());
            if (optionalPaymentMethod.isEmpty()) {
                failedToDelete.add(payload);
            } else {
                PaymentMethod paymentMethod = optionalPaymentMethod.get();
                User paymentMethodOwner = paymentMethod.getOwner();
                Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);

                if (optSessionUser.isEmpty()
                        || !optSessionUser.get().getUuid().equals(paymentMethodOwner.getUuid())) {
                    failedToDelete.add(payload);
                } else {
                    paymentMethodRepository.delete(paymentMethod);
                    successfullyDeleted.add(paymentMethod);
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
                        didAllFail ? "All provided payment-methods we're invalid values" : null,
                        response));
    }

}
