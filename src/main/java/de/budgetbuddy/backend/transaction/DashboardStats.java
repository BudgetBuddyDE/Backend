package de.budgetbuddy.backend.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardStats {
    private Double earnings;
    private Double upcoming_earnings;
    private Double expenses;
    private Double upcoming_expenses;
    private Double balance;

    public DashboardStats() {}
}
