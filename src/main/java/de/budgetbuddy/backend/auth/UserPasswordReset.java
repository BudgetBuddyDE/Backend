package de.budgetbuddy.backend.auth;

import de.budgetbuddy.backend.user.User;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "user_password_reset", schema = "public")
@Data
public class UserPasswordReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner")
    private User owner;

    @Column(name = "otp")
    private UUID otp;

    @Column(name = "used")
    private Boolean used;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt = new Date();

    public UserPasswordReset() {}

    public UserPasswordReset(User user) {
        this.owner = user;
        this.otp = UUID.randomUUID();
        this.used = false;
    }

    public boolean wasUsed() {
        return used;
    }

}
