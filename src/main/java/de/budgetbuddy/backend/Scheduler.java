package de.budgetbuddy.backend;

import de.budgetbuddy.backend.log.Log;
import de.budgetbuddy.backend.log.LogType;
import de.budgetbuddy.backend.log.Logger;
import de.budgetbuddy.backend.subscription.Subscription;
import de.budgetbuddy.backend.subscription.SubscriptionRepository;
import de.budgetbuddy.backend.transaction.Transaction;
import de.budgetbuddy.backend.transaction.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@EnableScheduling
public class Scheduler {
    private final SubscriptionRepository subscriptionRepository;
    private final TransactionRepository transactionRepository;

    @Autowired
    Scheduler(
            SubscriptionRepository subscriptionRepository,
            TransactionRepository transactionRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void myScheduledTask() {
        Logger.log(Log.builder()
                .application("Backend")
                .type(LogType.LOG)
                .category("process-subscriptions")
                .content("Starting process subscriptions")
                .build());
        LocalDate today = LocalDate.now();
        List<Subscription> subscriptions = subscriptionRepository
               .findAllByExecuteAtAndPaused(today.getDayOfMonth(), false);

        if (subscriptions.isEmpty()) {
           Logger.log(Log.builder()
                   .application("Backend")
                   .type(LogType.INFORMATION)
                   .category("process-subscriptions")
                   .content("No subscriptions to process")
                   .build());
           return;
        }

        List<Transaction> transactions = subscriptions.stream()
               .map(Transaction::ofSubscription)
               .toList();

        transactionRepository.saveAll(transactions);
        Logger.log(Log.builder()
                .application("Backend")
                .type(LogType.INFORMATION)
                .category("process-subscriptions")
                .content("Processed " + transactions.size() + " subscriptions")
                .build());
    }
}
