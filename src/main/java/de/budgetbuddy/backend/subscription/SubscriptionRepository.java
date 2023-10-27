package de.budgetbuddy.backend.subscription;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findAllByOwner(User owner);
    List<Subscription> findAllByOwnerAndPaused(User owner, Boolean paused);
}
