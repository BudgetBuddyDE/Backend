package de.budgetbuddy.backend.transaction;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class MonthlyBalance {
    private Date month;
    private Double balance;
    private Double income;
    private Double expenses;

    public MonthlyBalance(Date month, Double income, Double expenses, Double balance) {
        this.month = month;
        this.income = Math.abs(income);
        this.expenses = Math.abs(expenses);
        this.balance = balance;
    }
}
