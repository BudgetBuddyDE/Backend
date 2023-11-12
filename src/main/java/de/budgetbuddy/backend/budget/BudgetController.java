package de.budgetbuddy.backend.budget;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.auth.AuthorizationInterceptor;
import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.category.CategoryRepository;
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
@RequestMapping("/v1/budget")
public class BudgetController {
    private final BudgetRepository budgetRepository;
    private final BudgetProgressViewRepository budgetProgressViewRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public BudgetController(BudgetRepository budgetRepository, BudgetProgressViewRepository budgetProgressViewRepository, UserRepository userRepository, CategoryRepository categoryRepository) {
        this.budgetRepository = budgetRepository;
        this.budgetProgressViewRepository = budgetProgressViewRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Budget>> createBudget(@RequestBody Budget.Create payload, HttpSession session) throws JsonProcessingException {
        Optional<User> user = userRepository.findById(payload.getOwner());
        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided user doesn't exist"));
        }

        Optional<Category> category = categoryRepository.findByIdAndOwner(payload.getCategoryId(), user.get());
        if (category.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided category doesn't exist or isn't owned by you"));
        }

        if (budgetRepository.findByOwnerAndCategory(user.get(), category.get()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "There is already an budget for this category"));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(payload.getOwner())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't set a budget for different users"));
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(
                        200,
                        budgetRepository.save(new Budget(category.get(), user.get(), payload.getBudget()))));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Budget>>> getBudgetsByUuid(@RequestParam UUID uuid, HttpSession session) throws JsonProcessingException {
        Optional<User> user = userRepository.findById(uuid);
        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided user doesn't exist", new ArrayList<>()));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(uuid)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't retrieve budgets for different users", new ArrayList<>()));
        }

        List<Budget> budgets = budgetRepository.findAllByOwner(user.get());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(budgets));
    }

    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<List<BudgetProgressView>>> getBudgetProgressByUuid(@RequestParam UUID uuid, HttpSession session) throws JsonProcessingException {
        Optional<User> user = userRepository.findById(uuid);
        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided user doesn't exist", new ArrayList<>()));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(uuid)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't retrieve budgets for different users", new ArrayList<>()));
        }

        List<BudgetProgressView> budgets = budgetProgressViewRepository.findAllByOwner(user.get());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(budgets));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Budget>> updateBudget(@RequestBody Budget.Update payload, HttpSession session) throws JsonProcessingException {
        Optional<Budget> requestedBudget = budgetRepository.findById(payload.getBudgetId());
        if (requestedBudget.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided budget doesn't exist"));
        }

        Optional<Category> category = categoryRepository
                .findByIdAndOwner(payload.getCategoryId(), requestedBudget.get().getOwner());
        if (category.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided category doesn't exist"));
        }

        Budget budget = requestedBudget.get();
        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(budget.getOwner().getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't update budgets for different users"));
        }

        Budget updatedBudget = new Budget(budget.getId(), category.get(), budget.getOwner(), payload.getBudget(), budget.getCreatedAt());
        if (budgetRepository.findByOwnerAndCategory(budget.getOwner(), category.get()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "There is already an budget for this category"));
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(
                        200,
                        budgetRepository.save(updatedBudget)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Budget>> deleteBudget(@RequestBody Budget.Delete payload, HttpSession session) throws JsonProcessingException {
        Optional<Budget> budget = budgetRepository.findById(payload.getBudgetId());
        if (budget.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided budget doesn't exist"));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(budget.get().getOwner().getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't delete budgets from different users"));
        }


        budgetRepository.deleteById(payload.getBudgetId());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(200, budget.get()));
    }
}
