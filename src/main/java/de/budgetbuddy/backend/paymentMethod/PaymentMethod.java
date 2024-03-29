package de.budgetbuddy.backend.paymentMethod;

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
@Table(name = "payment_method", schema = "public")
@Data
@Builder
@AllArgsConstructor
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner")
    private User owner;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "address", length = 100, nullable = false)
    private String address;

    @Column(name = "provider", length = 100, nullable = false)
    private String provider;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt;

    public PaymentMethod() {}

    public PaymentMethod(User user, String name, String address, String provider, String description) {
        this.owner = user;
        this.name = name;
        this.address = address;
        this.provider = provider;
        this.description = description;
        this.createdAt = new Date();
    }

    public PaymentMethod.Delete toDelete() {
        return new PaymentMethod.Delete(this.id);
    }

    @Data
    public static class Create {
        public UUID owner;
        public String name;
        public String address;
        public String provider;
        public String description;
    }

    @Data
    public static class Update {
        public Long id;
        public String name;
        public String address;
        public String provider;
        public String description;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Delete {
        public Long paymentMethodId;
    }
}
