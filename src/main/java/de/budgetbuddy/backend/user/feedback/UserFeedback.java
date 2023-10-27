package de.budgetbuddy.backend.user.feedback;

import de.budgetbuddy.backend.user.User;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "user_feedback", schema = "public")
@Data
public class UserFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner", referencedColumnName = "uuid")
    private User owner;

    @Column(name = "rating", nullable = false)
    private Integer fileName;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message")
    private String message;

    @Column(name = "created_at", updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Date createdAt;

    public UserFeedback() {}

}
