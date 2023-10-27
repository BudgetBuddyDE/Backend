package de.budgetbuddy.backend.user.feedback;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {
    List<UserFeedback> findByOwner(User user);
}
