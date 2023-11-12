package de.budgetbuddy.backend.budget;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BudgetProgressViewRepository extends JpaRepository<BudgetProgressView, Long> {
    List<BudgetProgressView> findAllByOwner(User owner);
}
