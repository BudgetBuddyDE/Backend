package de.budgetbuddy.backend.transaction;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByOwner(User owner);
    List<Transaction> findAllByOwnerOrderByProcessedAtDesc(User owner);
}