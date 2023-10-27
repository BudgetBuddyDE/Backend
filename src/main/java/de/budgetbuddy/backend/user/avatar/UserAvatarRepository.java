package de.budgetbuddy.backend.user.avatar;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
    Optional<UserAvatar> findByOwner(User owner);
}
