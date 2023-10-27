package de.budgetbuddy.backend.category;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOwner(User owner);
    Optional<Category> findByIdAndOwner(Long id, User owner);
    Optional<Category> findByOwnerAndName(User owner, String name);
}
