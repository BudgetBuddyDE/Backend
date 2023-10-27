package de.budgetbuddy.backend.paymentMethod;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findAllByOwner(User user);
    Optional<PaymentMethod> findByIdAndOwner(Long id, User owner);
    Optional<PaymentMethod> findByOwnerAndNameAndAddress(User user, String name, String address);
}
