package com.rikkeibank.transaction.repository;

import com.rikkeibank.transaction.entity.SagaStepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SagaStepLogRepository extends JpaRepository<SagaStepLog, Long> {
    List<SagaStepLog> findByTransactionReferenceOrderByTimestampAsc(String transactionReference);
}
