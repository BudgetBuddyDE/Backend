package de.budgetbuddy.backend.user.avatar;

import de.budgetbuddy.backend.user.User;
import jakarta.persistence.*;
import java.util.Date;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

@Data
@Entity
@Table(name = "user_avatar", schema = "public")
public class UserAvatar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner", referencedColumnName = "uuid")
    private User owner;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mimetype", length = 20, nullable = false)
    private String mimetype;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt;

    public UserAvatar() {}

}

