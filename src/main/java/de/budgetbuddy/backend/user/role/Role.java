package de.budgetbuddy.backend.user.role;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.util.Date;

@Entity
@Table(name = "role", schema = "public")
@Data
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "role", length = 20, nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "permissions", nullable = false)
    private Integer permissions;


    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt;

    public Role() {}

    public Role(String name, String description, RolePermission permissions) {
        this.name = name;
        this.description = description;
        this.permissions = permissions.getPermissions();
        this.createdAt = new Date();
    }

    public Role(String name, String description, Integer permissions) {
        this.name = name;
        this.description = description;
        this.permissions = permissions;
        this.createdAt = new Date();
    }

    public Role(RolePermission role) {
        this.id = role.getId();
        this.name = role.name();
        this.permissions = role.getPermissions();
        this.createdAt = new Date();
    }

    public boolean isGreaterOrEqualThan(RolePermission role) {
        return permissions >= role.getPermissions();
    }

}
