package de.budgetbuddy.backend.log;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class Log {
    private Long id;
    private LogType type;
    /**
     * Comma seperated list
     */
    private String category;
    private String content;

    public Log(LogType type, String category, String content) {
        this.type = type;
        this.category = category;
        this.content = content;
    }

    public List<String> getCategories() {
        return List.of(this.category.split(","));
    }

    @Override
    public String toString() {
        return String.format("[%s]::%s::%s", type.toString(), category, content);
    }
}
