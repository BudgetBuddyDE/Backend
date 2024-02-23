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

import java.util.*;

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
        Optional<Budget> existingBudget = budgetRepository.findByOwnerAndCategory(budget.getOwner(), category.get());
        if (existingBudget.isPresent() && !existingBudget.get().getId().equals(payload.getBudgetId())) {
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
    public ResponseEntity<ApiResponse<Map<String, List<?>>>> deleteBudget(
            @RequestBody List<Budget.Delete> payloads,
            HttpSession session) throws JsonProcessingException {
        if (payloads.size() == 0) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(HttpStatus.BAD_REQUEST, "No budgets we're provided"));
        }

        List<Budget> successfullyDeleted = new ArrayList<>();
        List<Budget.Delete> failedToDelete = new ArrayList<>();

        for (Budget.Delete payload : payloads) {
            Optional<Budget> optBudget = budgetRepository.findById(payload.getBudgetId());
            if (optBudget.isEmpty()) {
                failedToDelete.add(payload);
            } else {
                Budget budget = optBudget.get();
                User budgetOwner = budget.getOwner();
                Optional<User> optSessionUser = AuthorizationInterceptor.getSessionUser(session);

                if (optSessionUser.isEmpty()
                        || !optSessionUser.get().getUuid().equals(budgetOwner.getUuid())) {
                    failedToDelete.add(payload);
                } else {
                    budgetRepository.delete(budget);
                    successfullyDeleted.add(budget);
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
                        didAllFail ? "All provided budgets we're invalid values" : null,
                        response));
    }
}
