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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 적립 1건을 나타내는 포인트 단위. 명세의 "특정 시점에 적립된 포인트"이며
 * {@code transactionId}로 식별된다. 잔액({@code remainingAmount})이 변하는 유일한 테이블로,
 * 사용 시 차감되고 사용취소 시 (만료 전이라면) 복원된다.
 */
@Entity
@Table(name = "point", indexes = {
        @Index(name = "idx_point_transaction_id", columnList = "transactionId", unique = true),
        @Index(name = "idx_point_user_status", columnList = "userId,status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Point {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 적립 트랜잭션 ID (명세의 A~E) */
    @Column(nullable = false, unique = true)
    private Long transactionId;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceType sourceType;

    /** 최초 적립 금액 (불변) */
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal originalAmount;

    /** 현재 사용 가능한 잔액 */
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal remainingAmount;

    @Column(nullable = false)
    private Instant earnedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointStatus status;

    @Version
    private Long version;

    private Point(Long transactionId, String userId, SourceType sourceType, BigDecimal amount,
                  Instant earnedAt, Instant expiresAt) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.sourceType = sourceType;
        this.originalAmount = amount;
        this.remainingAmount = amount;
        this.earnedAt = earnedAt;
        this.expiresAt = expiresAt;
        this.status = PointStatus.AVAILABLE;
    }

    /** 새 적립 포인트를 생성한다. */
    public static Point earn(Long transactionId, String userId, SourceType sourceType,
                             BigDecimal amount, Instant earnedAt, Instant expiresAt) {
        return new Point(transactionId, userId, sourceType, amount, earnedAt, expiresAt);
    }

    /**
     * 지정 시점 기준으로 만료되었는지 여부. 만료 순간({@code expiresAt == now})은 만료로
     * 취급한다 — {@code PointRepository}의 {@code expiresAt > :now} 조건과 경계가 일치한다.
     */
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** 적립취소 가능 여부: 미사용 상태(잔액=최초금액)이며 취소되지 않았어야 한다. */
    public boolean isFullyUnused() {
        return status != PointStatus.CANCELLED
                && remainingAmount.compareTo(originalAmount) == 0;
    }

    /** 잔액을 차감한다(사용). 잔액을 초과하면 예외. */
    public void consume(BigDecimal amount) {
        if (amount.compareTo(remainingAmount) > 0) {
            throw new IllegalArgumentException("소진 금액이 잔액을 초과합니다.");
        }
        this.remainingAmount = this.remainingAmount.subtract(amount);
        if (this.remainingAmount.signum() == 0) {
            this.status = PointStatus.EXHAUSTED;
        }
    }

    /** 잔액을 복원한다(사용취소, 만료 전). */
    public void restore(BigDecimal amount) {
        this.remainingAmount = this.remainingAmount.add(amount);
        if (this.status == PointStatus.EXHAUSTED && this.remainingAmount.signum() > 0) {
            this.status = PointStatus.AVAILABLE;
        }
    }

    /** 적립취소 처리: 잔액을 0으로 만들고 CANCELLED로 전환. */
    public void cancel() {
        this.remainingAmount = BigDecimal.ZERO;
        this.status = PointStatus.CANCELLED;
    }
}
