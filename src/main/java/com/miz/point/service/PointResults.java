package com.miz.point.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 서비스 계층이 반환하는 결과 타입 모음.
 */
public final class PointResults {

    private PointResults() {
    }

    /** 적립/수기지급 결과. */
    public record EarnResult(Long transactionId, BigDecimal amount, Instant expiresAt, BigDecimal balance) {
    }

    /** 적립취소 결과. */
    public record CancelEarnResult(Long transactionId, Long cancelledTransactionId, BigDecimal amount, BigDecimal balance) {
    }

    /** 사용 시 하나의 적립 포인트에서 소진된 내역. */
    public record UsageLine(Long transactionId, BigDecimal amount) {
    }

    /** 사용 결과. */
    public record UseResult(Long transactionId, String orderId, BigDecimal amount,
                            List<UsageLine> breakdown, BigDecimal balance) {
    }

    /** 사용취소 시 복원된 내역. reGranted=true면 만료로 인한 신규 복원 적립. */
    public record RestoreLine(Long transactionId, BigDecimal amount, boolean reGranted, Long newTransactionId) {
    }

    /** 사용취소 결과. */
    public record CancelUseResult(Long transactionId, Long cancelledUseTransactionId, BigDecimal amount,
                                  List<RestoreLine> restored, BigDecimal balance) {
    }
}
