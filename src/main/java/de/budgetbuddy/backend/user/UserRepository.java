package de.budgetbuddy.backend.user;

import org.reactivestreams.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUuidAndPassword(UUID uuid, String password);
    Optional<User> findByEmail(String email);
}
