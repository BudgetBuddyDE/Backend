package de.budgetbuddy.backend.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "user", schema = "public")
@Data
public class User {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID uuid;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "name", length = 30)
    private String name;

    @Column(name = "surname", length = 30)
    private String surname;

    @Column(name = "password")
    private String password;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt = new Date();

    public User() {}

    public User(UUID uuid) {
        this.uuid = uuid;
    }

    public void hashPassword() {
        password = BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

}