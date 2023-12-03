package de.budgetbuddy.backend.subscription;

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
@Table(name = "subscription", schema = "public")
@Data
@AllArgsConstructor
public class Subscription {

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

    @Column(name = "paused")
    private Boolean paused;

    /**
     * Day between 1 and 31
     */
    @Column(name = "execute_at")
    private int executeAt;

    @Column(name = "receiver", length = 80, nullable = false)
    private String receiver;

    @Column(name = "description")
    private String description;

    @Column(name = "transfer_amount", nullable = false)
    private Double transferAmount;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt;

    public Subscription() {}

    public Subscription(User owner, Category category, PaymentMethod paymentMethod, boolean paused, int executeAt, String receiver, String description, Double transferAmount) {
        this.owner = owner;
        this.category = category;
        this.paymentMethod = paymentMethod;
        this.paused = paused;
        this.executeAt = executeAt;
        this.receiver = receiver;
        this.description = description;
        this.transferAmount = transferAmount;
        this.createdAt = new Date();
    }

    public static boolean isValidExecutionDate(int executeAt) {
        return executeAt >= 1 && executeAt <=31;
    }

    @Data
    public static class Create {
        private UUID owner;
        private Long categoryId;
        private Long paymentMethodId;
        private Boolean paused;
        private String test;
        private int executeAt;
        private String receiver;
        private String description;
        private Double transferAmount;
    }

    @Data
    public static class Update {
        private Long subscriptionId;
        private Long categoryId;
        private Long paymentMethodId;
        private Boolean paused;
        private int executeAt;
        private String receiver;
        private String description;
        private Double transferAmount;
    }

    @Data
    public static class Delete {
        private Long subscriptionId;
    }

}
