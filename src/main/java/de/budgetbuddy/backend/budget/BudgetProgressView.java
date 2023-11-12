package de.budgetbuddy.backend.budget;

import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.user.User;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.Immutable;

import java.util.Date;

@Data
@Immutable
@Entity(name = "v_budget_progress")
public class BudgetProgressView {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "category", nullable = false)
    private Category category;

    @ManyToOne
    @JoinColumn(name = "owner", nullable = false)
    private User owner;

    @Column(name = "budget", nullable = false)
    private Double budget;

    @Column(name = "amount_spent", nullable = false)
    private Double amount_spent;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt;

    public BudgetProgressView() {}
}
