package de.budgetbuddy.backend.log;

import org.springframework.stereotype.Component;

@Component
public class Logger {
    private static Logger instance;
    private LogRepository logRepository;

    public void setLogRepository(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public static Logger getInstance() {
        if (instance == null) {
            synchronized (Logger.class) {
                if (instance == null) instance = new Logger();
            }
        }
        return instance;
    }

    public void log(Log log) {
        System.out.println(log);
        if (this.logRepository == null) return;
        logRepository.save(log);
    }

    public void log(String application, LogType type, String category, String content) {
        Log log = new Log(application, type, category, content);
        System.out.println(log);
        if (this.logRepository == null) return;
        logRepository.save(log);
    }
}
