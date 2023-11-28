package de.budgetbuddy.backend.user;

import org.reactivestreams.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUuidAndPassword(UUID uuid, String password);
    Optional<User> findByEmail(String email);

    @Query(value = "select * from public.user", nativeQuery = true)
    List<User> test();


}
