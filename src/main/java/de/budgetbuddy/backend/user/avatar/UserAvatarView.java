package de.budgetbuddy.backend.user.avatar;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.Immutable;

import java.util.Date;
import java.util.UUID;

@Data
@Immutable
@Entity(name = "v_user_avatar")
public class UserAvatarView {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "name", length = 30)
    private String name;

    @Column(name = "surname", length = 30)
    private String surname;

    @Column(name = "password")
    private String password;

    @Column(name = "avatar_id")
    private Long avatarId;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "avatar_mimetype")
    private String avatarMimetype;

    @Column(name = "created_at", updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Date createdAt;

    public UserAvatarView() {}

}
