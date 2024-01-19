package de.budgetbuddy.backend.category;

import de.budgetbuddy.backend.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "category", schema = "public")
@Data
@Builder
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner")
    private User owner;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt;

    public Category() {}

    public Category(User owner, String name, String description) {
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.createdAt = new Date();
    }

    public Category.Delete toDelete() {
        return new Category.Delete(this.id);
    }

    @Data
    public static class Create {
        public UUID owner;
        public String name;
        public String description;
    }

    @Data
    public static class Update {
        public Long categoryId;
        public String name;
        public String description;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Delete {
        public Long categoryId;
    }

}
