package de.budgetbuddy.backend.transaction.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionFileRepository extends JpaRepository<TransactionFile, UUID> {
}
