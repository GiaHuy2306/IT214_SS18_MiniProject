package com.rikkeibank.transaction.repository;

import com.rikkeibank.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionReference(String reference);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccountNumber = :acc OR t.targetAccountNumber = :acc) ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountNumber(@Param("acc") String accountNumber);

    List<Transaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Transaction> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
}
