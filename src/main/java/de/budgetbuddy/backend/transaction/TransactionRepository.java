package de.budgetbuddy.backend.transaction;

import de.budgetbuddy.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByIdAndOwner(Long id, User owner);
    List<Transaction> findAllByOwner(User owner);
    List<Transaction> findTransactionsByOwnerOrderByProcessedAtDesc(User owner);
    @Query(value = "select * from f_get_daily_transactions(:start_date, :end_date, :requested_data, :user_id)", nativeQuery = true)
    List<Object[]> fetchDailyTransactions(
            @Param("start_date") LocalDate startDate,
            @Param("end_date") LocalDate endDate,
            @Param("requested_data") String requestedData,
            @Param("user_id") UUID userId
    );

    @Query(value = "select sum(amount) from f_get_daily_transactions(:start_date, :end_date, :data_type, :user_id)", nativeQuery = true)
    Double getTransactionSumByDateRange(@Param("start_date") LocalDate startDate,
                                @Param("end_date") LocalDate endDate,
                                @Param("user_id") UUID userId,
                                @Param("data_type") String dataType);

    @Query(value = "select sum(amount) from f_get_daily_transactions(:start_date, :end_date, 'BALANCE', :user_id)", nativeQuery = true)
    Double getBalance(@Param("start_date") LocalDate startDate,
                      @Param("end_date") LocalDate endDate,
                      @Param("user_id") UUID userId);

    @Query("select new de.budgetbuddy.backend.transaction.MonthlyBalance(" +
            "date_trunc('month', t.processedAt), " +
            "sum(case when t.transferAmount >= 0 then t.transferAmount else 0 end), " +
            "sum(case when t.transferAmount < 0 then t.transferAmount else 0 end), " +
            "sum(t.transferAmount)) " +
            "from Transaction t where t.owner = :user " +
            "group by date_trunc('month', t.processedAt) " +
            "order by date_trunc('month', t.processedAt) desc")
    List<MonthlyBalance> getMonthlyBalance(@Param("user") User user);
}
