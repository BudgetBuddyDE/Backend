package de.budgetbuddy.backend;

import de.budgetbuddy.backend.subscription.Subscription;
import de.budgetbuddy.backend.subscription.SubscriptionRepository;
import de.budgetbuddy.backend.transaction.Transaction;
import de.budgetbuddy.backend.transaction.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
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
        MDC.put("category", "process-subscriptions");
        log.info("Starting to process subscriptions");
        LocalDate today = LocalDate.now();
        List<Subscription> subscriptions = subscriptionRepository
               .findAllByExecuteAtAndPaused(today.getDayOfMonth(), false);

        if (subscriptions.isEmpty()) {
           log.info("No subscriptions to process");
           return;
        }

        List<Transaction> transactions = subscriptions.stream()
               .map(Transaction::ofSubscription)
               .toList();

        transactionRepository.saveAll(transactions);
        log.info("Processed {} subscriptions", transactions.size());
        MDC.clear();
    }
}
