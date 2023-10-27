package de.budgetbuddy.backend.budget;

import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "budget", schema = "public")
@Data
@AllArgsConstructor
public class Budget {

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

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt;

    public Budget() {}

    public Budget(Category category, User owner, Double budget) {
        this.category = category;
        this.owner = owner;
        this.budget = budget;
        this.createdAt = new Date();
    }

    @Data
    public static class Create {
        private Long categoryId;
        private UUID owner;
        private Double budget;
    }

    @Data
    public static class Update {
        private Long budgetId;
        private Long categoryId;
        private Double budget;
    }

    @Data
    public static class Delete {
        private Long budgetId;
    }

}
