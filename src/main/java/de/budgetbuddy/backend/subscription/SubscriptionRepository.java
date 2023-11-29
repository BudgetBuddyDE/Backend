package de.budgetbuddy.backend.subscription;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findAllByOwner(User owner);
    List<Subscription> findAllByOwnerAndPaused(User owner, Boolean paused);

    @Query(value = "select coalesce(sum(subscription.transfer_amount), 0) as upcoming_income_subscriptions\n" +
            "from subscription\n" +
            "where subscription.owner = :uuid\n" +
            "  and subscription.paused = false\n" +
            "  and subscription.transfer_amount > 0\n" +
            "  and subscription.execute_at > extract(DAY from now())", nativeQuery = true)
    Double getUpcomingSubscriptioEarnings(@Param("uuid") UUID uuid);
    @Query(value = "select coalesce(sum(subscription.transfer_amount), 0) as upcoming_income_subscriptions\n" +
            "from subscription\n" +
            "where subscription.owner = :uuid\n" +
            "  and subscription.paused = false\n" +
            "  and subscription.transfer_amount < 0\n" +
            "  and subscription.execute_at > extract(DAY from now())", nativeQuery = true)
    Double getUpcomingSubscriptionExpenses(@Param("uuid") UUID uuid);
}
