package com.miz.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하나의 USE 트랜잭션이 어떤 적립 포인트에서 얼마를 소진했는지 연결하는 상세 기록.
 * 사용취소 시 어떤 포인트를 어떤 순서(id ASC = 소진 순서)로 얼마나 복원할지 판단하는
 * 근거가 된다.
 */
@Entity
@Table(name = "point_usage_detail", indexes = {
        @Index(name = "idx_usage_use_tx", columnList = "useTransactionId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소진을 발생시킨 USE 트랜잭션의 ID (예시의 C) */
    @Column(nullable = false)
    private Long useTransactionId;

    /** 소진된 적립 포인트의 transactionId (예시의 A/B) */
    @Column(nullable = false)
    private Long pointTransactionId;

    /** 이 포인트에서 소진한 금액 (불변) */
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amountUsed;

    /** 지금까지 사용취소된 누적 금액 */
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal cancelledAmount;

    @Version
    private Long version;

    private PointUsageDetail(Long useTransactionId, Long pointTransactionId, BigDecimal amountUsed) {
        this.useTransactionId = useTransactionId;
        this.pointTransactionId = pointTransactionId;
        this.amountUsed = amountUsed;
        this.cancelledAmount = BigDecimal.ZERO;
    }

    public static PointUsageDetail of(Long useTransactionId, Long pointTransactionId, BigDecimal amountUsed) {
        return new PointUsageDetail(useTransactionId, pointTransactionId, amountUsed);
    }

    /** 아직 사용취소 가능한 잔여 금액. */
    public BigDecimal cancellableAmount() {
        return amountUsed.subtract(cancelledAmount);
    }

    /** 사용취소 금액을 누적한다. 취소가능액을 초과하면 예외. */
    public void addCancelled(BigDecimal amount) {
        if (amount.compareTo(cancellableAmount()) > 0) {
            throw new IllegalArgumentException("사용취소 금액이 취소가능액을 초과합니다.");
        }
        this.cancelledAmount = this.cancelledAmount.add(amount);
    }
}
