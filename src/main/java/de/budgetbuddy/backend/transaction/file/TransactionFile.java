package de.budgetbuddy.backend.transaction.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.budgetbuddy.backend.transaction.Transaction;
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
@Table(name = "transaction_file", schema = "public")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionFile {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID uuid;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "owner")
    private User owner;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "transaction")
    private Transaction transaction;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private int fileSize;

    @Column(name = "mimetype", length = 20)
    private String mimeType;

    @Column(name = "location", length = 100)
    private String location;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Date createdAt = new Date();

    @Data
    public static class Create {
        private String fileName;
        private int fileSize;
        private String mimeType;
        private String fileUrl;
    }

}
