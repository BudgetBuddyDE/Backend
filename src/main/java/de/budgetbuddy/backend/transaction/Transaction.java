package de.budgetbuddy.backend.transaction;

import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.paymentMethod.PaymentMethod;
import de.budgetbuddy.backend.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "transaction", schema = "public")
@Data
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
    private Category category;

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

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt;

    public Transaction() {}

    public Transaction(User owner, Category category, PaymentMethod paymentMethod, Date processedAt, String receiver, String description, Double transferAmount) {
        this.owner = owner;
        this.category = category;
        this.paymentMethod = paymentMethod;
        this.processedAt = processedAt;
        this.receiver = receiver;
        this.description = description;
        this.transferAmount = transferAmount;
        this.createdAt = new Date();
    }

    @Data
    public static class DailyTransaction {
        private Date date;
        private Double amount;
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
    public static class Delete {
        private Long transactionId;
    }

}
