package com.miz.point.repository;

import com.miz.point.domain.PointTransaction;
import com.miz.point.domain.TransactionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Optional<PointTransaction> findByIdAndType(Long id, TransactionType type);
}
