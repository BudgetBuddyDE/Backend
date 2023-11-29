package de.budgetbuddy.backend.transaction;

import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<Transaction.DailyTransaction> getDailyTransactions(LocalDate startDate, LocalDate endDate, DailyTransactionType requestedData, UUID uuid) {
        List<Transaction.DailyTransaction> dailyTransactions = new ArrayList<>();
        for (Object[] row : transactionRepository.fetchDailyTransactions(startDate, endDate, requestedData.name(), uuid)) {
            Transaction.DailyTransaction dailyTransaction = new Transaction.DailyTransaction();
            dailyTransaction.setDate((Date) row[0]);
            dailyTransaction.setAmount((Double) row[1]);
            dailyTransactions.add(dailyTransaction);
        }

        return dailyTransactions;
    }
}
