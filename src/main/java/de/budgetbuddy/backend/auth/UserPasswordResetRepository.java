package de.budgetbuddy.backend.auth;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPasswordResetRepository extends JpaRepository<UserPasswordReset, Long> {
    Optional<UserPasswordReset> findByOtp(UUID otp);
    List<UserPasswordReset> findAllByOwner(User user);
}
