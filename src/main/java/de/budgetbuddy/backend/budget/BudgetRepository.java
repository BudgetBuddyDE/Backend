package de.budgetbuddy.backend.budget;

import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findAllByOwner(User owner);
    Optional<Budget> findByOwnerAndCategory(User owner, Category category);
}
