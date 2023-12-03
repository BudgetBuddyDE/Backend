package de.budgetbuddy.backend.transaction;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByOwner(User owner);
    List<Transaction> findAllByOwnerOrderByProcessedAtDesc(User owner);
    @Query(value = "select * from f_get_daily_transactions(:start_date, :end_date, :requested_data, :user_id)", nativeQuery = true)
    List<Object[]> fetchDailyTransactions(
            @Param("start_date") LocalDate startDate,
            @Param("end_date") LocalDate endDate,
            @Param("requested_data") String requestedData,
            @Param("user_id") UUID userId
    );

    @Query(value = "select sum(amount) from f_get_daily_transactions(:start_date, :end_date, 'INCOME', :user_id)", nativeQuery = true)
    Double getReceivedEarnings(@Param("start_date") LocalDate startDate,
                               @Param("end_date") LocalDate endDate,
                               @Param("user_id") UUID userId);

    @Query(value = "select sum(amount) from f_get_daily_transactions(:start_date, :end_date, 'SPENDINGS', :user_id)", nativeQuery = true)
    Double getPaidExpenses(@Param("start_date") LocalDate startDate,
                           @Param("end_date") LocalDate endDate,
                           @Param("user_id") UUID userId);

    @Query(value = "select sum(amount) from f_get_daily_transactions(:start_date, :end_date, 'BALANCE', :user_id)", nativeQuery = true)
    Double getBalance(@Param("start_date") LocalDate startDate,
                      @Param("end_date") LocalDate endDate,
                      @Param("user_id") UUID userId);
}
