package com.miz.point.service;

import com.miz.point.config.PointProperties;
import com.miz.point.domain.Point;
import com.miz.point.domain.PointTransaction;
import com.miz.point.domain.SourceType;
import com.miz.point.domain.TransactionType;
import com.miz.point.domain.UserPointAccount;
import com.miz.point.exception.PointExceptions;
import com.miz.point.domain.PointUsageDetail;
import com.miz.point.repository.PointRepository;
import com.miz.point.repository.PointTransactionRepository;
import com.miz.point.repository.PointUsageDetailRepository;
import com.miz.point.repository.UserPointAccountRepository;
import com.miz.point.service.PointResults.CancelEarnResult;
import com.miz.point.service.PointResults.CancelUseResult;
import com.miz.point.service.PointResults.EarnResult;
import com.miz.point.service.PointResults.RestoreLine;
import com.miz.point.service.PointResults.UsageLine;
import com.miz.point.service.PointResults.UseResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 무료 포인트 시스템의 핵심 서비스. 적립/적립취소/사용/사용취소를 처리한다.
 * 각 변경 작업은 사용자 계정 행을 비관적 락으로 잠근 뒤 진행한다.
 */
@Service
public class PointService {

    private final PointRepository pointRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointUsageDetailRepository usageDetailRepository;
    private final UserPointAccountRepository accountRepository;
    private final PointProperties properties;
    private final Clock clock;

    public PointService(PointRepository pointRepository,
                        PointTransactionRepository transactionRepository,
                        PointUsageDetailRepository usageDetailRepository,
                        UserPointAccountRepository accountRepository,
                        PointProperties properties,
                        Clock clock) {
        this.pointRepository = pointRepository;
        this.transactionRepository = transactionRepository;
        this.usageDetailRepository = usageDetailRepository;
        this.accountRepository = accountRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** 일반 적립. */
    @Transactional
    public EarnResult earn(String userId, BigDecimal amount, Integer expiryDays) {
        return doEarn(userId, amount, expiryDays, SourceType.EARN);
    }

    /** 관리자 수기지급. 다른 적립과 구분되며 사용 시 우선 소진된다. */
    @Transactional
    public EarnResult manualEarn(String userId, BigDecimal amount, Integer expiryDays) {
        return doEarn(userId, amount, expiryDays, SourceType.MANUAL);
    }

    private EarnResult doEarn(String userId, BigDecimal amount, Integer expiryDays, SourceType sourceType) {
        validateEarnAmount(amount);
        int days = resolveAndValidateExpiryDays(expiryDays);

        lockAccount(userId);
        Instant now = clock.instant();

        BigDecimal balance = currentBalance(userId, now);
        if (balance.add(amount).compareTo(properties.getMaxBalance()) > 0) {
            throw new PointExceptions.MaxBalanceExceeded(
                    "최대 보유 잔액(%s)을 초과합니다.".formatted(properties.getMaxBalance()));
        }

        Instant expiresAt = now.plus(days, ChronoUnit.DAYS);
        PointTransaction tx = transactionRepository.saveAndFlush(PointTransaction.of(
                userId, TransactionType.EARN, amount, null, null, now));
        Long transactionId = tx.getId();
        pointRepository.save(Point.earn(transactionId, userId, sourceType, amount, now, expiresAt));

        return new EarnResult(transactionId, amount, expiresAt, balance.add(amount));
    }

    /** 적립취소. 일부라도 사용된 적립은 취소할 수 없다. */
    @Transactional
    public CancelEarnResult cancelEarn(Long transactionId) {
        Point point = pointRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PointExceptions.NotFound("적립 포인트를 찾을 수 없습니다: " + transactionId));

        lockAccount(point.getUserId());

        if (!point.isFullyUnused()) {
            throw new PointExceptions.EarnAlreadyUsed(
                    "이미 사용되었거나 취소된 적립은 취소할 수 없습니다: " + transactionId);
        }

        BigDecimal amount = point.getOriginalAmount();
        point.cancel();

        Instant now = clock.instant();
        PointTransaction tx = transactionRepository.saveAndFlush(PointTransaction.of(
                point.getUserId(), TransactionType.CANCEL_EARN, amount, null, transactionId, now));
        Long cancelTransactionId = tx.getId();

        BigDecimal balance = currentBalance(point.getUserId(), now);
        return new CancelEarnResult(cancelTransactionId, transactionId, amount, balance);
    }

    /**
     * 포인트 사용. 수기지급(MANUAL) 우선, 이후 만료 임박 순으로 소진한다.
     * 소진 내역을 PointUsageDetail로 기록해 사용취소 시 복원 근거로 삼는다.
     */
    @Transactional
    public UseResult use(String userId, String orderId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new PointExceptions.InvalidAmount("사용 금액은 1 이상이어야 합니다.");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new PointExceptions.MissingOrderId("주문번호는 필수입니다.");
        }

        lockAccount(userId);
        Instant now = clock.instant();

        BigDecimal balance = currentBalance(userId, now);
        if (balance.compareTo(amount) < 0) {
            throw new PointExceptions.InsufficientBalance(
                    "가용 잔액(%s)이 부족합니다.".formatted(balance));
        }

        PointTransaction useTx = transactionRepository.saveAndFlush(PointTransaction.of(
                userId, TransactionType.USE, amount, orderId, null, now));
        Long useTransactionId = useTx.getId();
        List<Point> candidates = pointRepository.findUsableOrdered(userId, now);

        BigDecimal remaining = amount;
        List<UsageLine> breakdown = new ArrayList<>();
        for (Point point : candidates) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal take = remaining.min(point.getRemainingAmount());
            point.consume(take);
            usageDetailRepository.save(PointUsageDetail.of(useTransactionId, point.getTransactionId(), take));
            breakdown.add(new UsageLine(point.getTransactionId(), take));
            remaining = remaining.subtract(take);
        }

        // 잔액부족은 위에서 이미 걸렀으므로 정확히 amount만큼 소진됐다. SUM 재쿼리 없이
        // 사전 잔액에서 차감해 응답한다(earn과 동일 패턴).
        return new UseResult(useTransactionId, orderId, amount, breakdown, balance.subtract(amount));
    }

    /**
     * 사용취소. 대상 USE의 소진 내역을 소진 순서대로 취소한다.
     * 취소 대상 포인트가 만료 전이면 원래 포인트에 복원하고, 이미 만료되었다면 원래
     * 포인트는 건드리지 않고 그 금액만큼 신규 RESTORE 포인트로 재적립한다.
     */
    @Transactional
    public CancelUseResult cancelUse(Long useTransactionId, BigDecimal amount) {
        PointTransaction useTx = transactionRepository
                .findByIdAndType(useTransactionId, TransactionType.USE)
                .orElseThrow(() -> new PointExceptions.NotFound(
                        "사용 트랜잭션을 찾을 수 없습니다: " + useTransactionId));

        if (amount == null || amount.signum() <= 0) {
            throw new PointExceptions.InvalidAmount("사용취소 금액은 1 이상이어야 합니다.");
        }

        String userId = useTx.getUserId();
        lockAccount(userId);
        Instant now = clock.instant();

        List<PointUsageDetail> details =
                usageDetailRepository.findByUseTransactionIdOrderByIdAsc(useTransactionId);
        BigDecimal cancellable = details.stream()
                .map(PointUsageDetail::cancellableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(cancellable) > 0) {
            throw new PointExceptions.CancelAmountExceeded(
                    "취소가능액(%s)을 초과합니다.".formatted(cancellable));
        }

        // CANCEL_USE 트랜잭션을 먼저 저장해 순번(D)을 확보한다. 만료분 RESTORE(E)는 이후.
        PointTransaction cancelTx = transactionRepository.saveAndFlush(PointTransaction.of(
                userId, TransactionType.CANCEL_USE, amount, useTx.getOrderId(), useTransactionId, now));
        Long cancelTransactionId = cancelTx.getId();

        BigDecimal remaining = amount;
        List<RestoreLine> restored = new ArrayList<>();

        for (PointUsageDetail detail : details) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal open = detail.cancellableAmount();
            if (open.signum() == 0) {
                continue;
            }
            BigDecimal take = remaining.min(open);
            detail.addCancelled(take);

            Point point = pointRepository.findByTransactionId(detail.getPointTransactionId()).orElseThrow();
            if (point.isExpired(now)) {
                Instant expiresAt = now.plus(properties.getDefaultExpiryDays(), ChronoUnit.DAYS);
                PointTransaction restoreTx = transactionRepository.saveAndFlush(PointTransaction.of(
                        userId, TransactionType.RESTORE, take, useTx.getOrderId(), useTransactionId, now));
                Long newTransactionId = restoreTx.getId();
                pointRepository.save(Point.earn(newTransactionId, userId, SourceType.RESTORE, take, now, expiresAt));
                restored.add(new RestoreLine(point.getTransactionId(), take, true, newTransactionId));
            } else {
                point.restore(take);
                restored.add(new RestoreLine(point.getTransactionId(), take, false, null));
            }
            remaining = remaining.subtract(take);
        }

        return new CancelUseResult(cancelTransactionId, useTransactionId, amount, restored, currentBalance(userId, now));
    }

    /** 사용자의 현재 가용 잔액. */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String userId) {
        return currentBalance(userId, clock.instant());
    }

    // --- 내부 헬퍼 ---

    private BigDecimal currentBalance(String userId, Instant now) {
        return pointRepository.sumAvailableBalance(userId, now);
    }

    private void validateEarnAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new PointExceptions.InvalidAmount("적립 금액은 1 이상이어야 합니다.");
        }
        if (amount.compareTo(properties.getMaxPerEarn()) > 0) {
            throw new PointExceptions.InvalidAmount(
                    "1회 최대 적립 금액(%s)을 초과합니다.".formatted(properties.getMaxPerEarn()));
        }
    }

    private int resolveAndValidateExpiryDays(Integer expiryDays) {
        int days = (expiryDays == null) ? properties.getDefaultExpiryDays() : expiryDays;
        if (days < properties.getMinExpiryDays()) {
            throw new PointExceptions.InvalidExpiry(
                    "만료일은 최소 %d일 이상이어야 합니다.".formatted(properties.getMinExpiryDays()));
        }
        // "5년 미만": 부여 만료 시점이 지금부터 maxExpiryYears년 이후가 되면 안 된다.
        Instant now = clock.instant();
        if (!now.plus(days, ChronoUnit.DAYS).isBefore(now.atZone(java.time.ZoneOffset.UTC)
                .plusYears(properties.getMaxExpiryYears()).toInstant())) {
            throw new PointExceptions.InvalidExpiry(
                    "만료일은 %d년 미만이어야 합니다.".formatted(properties.getMaxExpiryYears()));
        }
        return days;
    }

    private void lockAccount(String userId) {
        accountRepository.findByIdForUpdate(userId)
                .orElseGet(() -> {
                    UserPointAccount created = accountRepository.save(
                            UserPointAccount.of(userId, clock.instant()));
                    // 생성 직후 락 확보 (동시 최초 진입 시 한쪽은 위 findByIdForUpdate로 직렬화)
                    return accountRepository.findByIdForUpdate(userId).orElse(created);
                });
    }
}
