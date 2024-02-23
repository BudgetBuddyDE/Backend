package de.budgetbuddy.backend.transaction;

import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.paymentMethod.PaymentMethod;
import de.budgetbuddy.backend.subscription.Subscription;
import de.budgetbuddy.backend.transaction.file.TransactionFile;
import de.budgetbuddy.backend.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transaction", schema = "public")
@Data
@Builder
@AllArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner", nullable = false)
    private User owner;

    @OneToOne
    @JoinColumn(name = "category", nullable = false)
    private de.budgetbuddy.backend.category.Category category;

    @OneToOne
    @JoinColumn(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "processed_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date processedAt;

    @Column(name = "receiver", length = 80, nullable = false)
    private String receiver;

    @Column(name = "description")
    private String description;

    @Column(name = "transfer_amount", nullable = false)
    private Double transferAmount;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    private List<TransactionFile> attachedFiles;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt;

    public Transaction() {}

    public Transaction.Delete toDelete() {
        return new Transaction.Delete(this.id);
    }

    @Data
    public static class DailyTransaction {
        private Date date;
        private Double amount;
    }

    public static Transaction ofSubscription(Subscription subscription) {
        Date processedDate = new Date();
        processedDate.setDate(subscription.getExecuteAt());
        return Transaction.builder()
                .owner(subscription.getOwner())
                .processedAt(processedDate)
                .category(subscription.getCategory())
                .paymentMethod(subscription.getPaymentMethod())
                .receiver(subscription.getReceiver())
                .description(subscription.getDescription())
                .transferAmount(subscription.getTransferAmount())
                .attachedFiles(new ArrayList<>())
                .createdAt(new Date())
                .build();
    }

    @Data
    public static class Create {
        private UUID owner;
        private Long categoryId;
        private Long paymentMethodId;
        private Date processedAt;
        private String receiver;
        private String description;
        private Double transferAmount;
    }

    @Data
    public static class Update {
        private Long transactionId;
        private Long categoryId;
        private Long paymentMethodId;
        private Date processedAt;
        private String receiver;
        private String description;
        private Double transferAmount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Delete {
        private Long transactionId;
    }
}
