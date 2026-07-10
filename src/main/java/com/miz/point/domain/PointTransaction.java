package com.miz.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모든 포인트 행위(적립/적립취소/사용/사용취소/복원)를 기록하는 append-only 원장.
 * 저장 후 변경되지 않는다.
 */
@Entity
@Table(name = "point_transaction", indexes = {
        @Index(name = "idx_tx_user", columnList = "userId"),
        @Index(name = "idx_tx_order", columnList = "orderId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    /** 사용/사용취소 시 주문번호 */
    @Column
    private String orderId;

    /**
     * 연관 트랜잭션 ID.
     * CANCEL_EARN → 취소 대상 적립의 transactionId,
     * USE → null,
     * CANCEL_USE → 취소 대상 USE 트랜잭션의 id,
     * RESTORE → 복원을 유발한 USE 트랜잭션의 id.
     */
    @Column
    private Long relatedTransactionId;

    @Column(nullable = false)
    private Instant createdAt;

    private PointTransaction(String userId, TransactionType type, BigDecimal amount,
                             String orderId, Long relatedTransactionId, Instant createdAt) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.orderId = orderId;
        this.relatedTransactionId = relatedTransactionId;
        this.createdAt = createdAt;
    }

    public static PointTransaction of(String userId, TransactionType type, BigDecimal amount,
                                      String orderId, Long relatedTransactionId, Instant createdAt) {
        return new PointTransaction(userId, type, amount, orderId, relatedTransactionId, createdAt);
    }
}
