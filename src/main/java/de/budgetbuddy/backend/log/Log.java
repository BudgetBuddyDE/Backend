package de.budgetbuddy.backend.log;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.util.Date;
import java.util.List;

@Entity
@Table(name = "log", schema = "public")
@Data
@AllArgsConstructor
public class Log {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "application")
    private String application;

    @Column(name = "type")
    private LogType type;
    /**
     * Comma seperated list
     */
    @Column(name = "category")
    private String category;

    @Column(name = "content")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt;

    public Log() {
        this.createdAt = new Date();
    }

    public Log(String application, LogType type, String category, String content) {
        this.application = application;
        this.type = type;
        this.category = category;
        this.content = content;
        this.createdAt = new Date();
    }

    public List<String> getCategories() {
        return List.of(this.category.split(","));
    }

    @Override
    public String toString() {
        return String.format("[%s]::%s::%s", type.toString(), category, content);
    }
}
