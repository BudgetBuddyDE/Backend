package de.budgetbuddy.backend.category;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.auth.AuthorizationInterceptor;
import de.budgetbuddy.backend.user.UserRepository;
import de.budgetbuddy.backend.user.User;
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
@RequestMapping("/v1/category")
public class CategoryController {
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Autowired
    public CategoryController(CategoryRepository categoryRepository, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Category>> createCategory(@RequestBody Category.Create payload, HttpSession session) throws JsonProcessingException {
        Optional<User> user = userRepository.findById(payload.getOwner());
        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided user doesn't exist"));
        }

        if (categoryRepository.findByOwnerAndName(user.get(), payload.getName()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "There is already an category by this name"));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(payload.getOwner())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't create categories for other users"));
        }

        Category category = new Category(user.get(), payload.getName(), payload.getDescription());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(categoryRepository.save(category)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Category>>> getCategoriesByUuid(@RequestParam UUID uuid, HttpSession session) throws JsonProcessingException {
        Optional<User> user = userRepository.findById(uuid);

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(uuid)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't retrieve categories from different users"));
        }

        return user.map(value -> ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(categoryRepository.findAllByOwner(value)))).orElseGet(() -> ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "Provided user doesn't exist", new ArrayList<>())));

    }

    @PutMapping
    public ResponseEntity<ApiResponse<Category>> updateCategory(@RequestBody Category.Update payload, HttpSession session) throws JsonProcessingException {
        Optional<Category> requestedCategory = categoryRepository.findById(payload.getCategoryId());
        if (requestedCategory.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided category doesn't exist"));
        }
        Category category = requestedCategory.get();

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(category.getOwner().getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't modify categories from other users"));
        }

        Optional<Category> alreadyExistingCategory = categoryRepository.findByOwnerAndName(sessionUser.get(), payload.getName());
        if (alreadyExistingCategory.isPresent()
                && !alreadyExistingCategory.get().getId().equals(payload.getCategoryId())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "There is already an category by this name"));
        }

        Category updatedCategory = new Category(category.getId(), category.getOwner(), payload.getName(), payload.getDescription(), category.getCreatedAt());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(categoryRepository.save(updatedCategory)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Category>> deleteCategory(@RequestBody Category.Delete payload, HttpSession session) throws JsonProcessingException {
        Optional<Category> category =  categoryRepository.findById(payload.getCategoryId());
        if (category.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided category doesn't exist"));
        }

        Optional<User> sessionUser = AuthorizationInterceptor.getSessionUser(session);
        if (sessionUser.isEmpty()) {
            return AuthorizationInterceptor.noValidSessionResponse();
        } else if (!sessionUser.get().getUuid().equals(category.get().getOwner().getUuid())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(HttpStatus.CONFLICT.value(), "You can't delete categories from other users"));
        }

        categoryRepository.deleteById(payload.getCategoryId());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(200, category.get()));
    }
}
