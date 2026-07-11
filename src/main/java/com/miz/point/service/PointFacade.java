package com.miz.point.service;

import com.miz.point.dto.request.CancelUseRequest;
import com.miz.point.dto.request.EarnRequest;
import com.miz.point.dto.request.UseRequest;
import com.miz.point.exception.PointExceptions;
import com.miz.point.service.PointResults.CancelEarnResult;
import com.miz.point.service.PointResults.CancelUseResult;
import com.miz.point.service.PointResults.EarnResult;
import com.miz.point.service.PointResults.UseResult;
import java.math.BigDecimal;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 멱등성 래핑 파사드. 각 변경 API를 멱등키로 감싸 1회만 반영되도록 한다.
 * 요청 지문(fingerprint)을 만들어 IdempotencyService에 위임하고, 순수 비즈니스
 * 로직은 PointService에 그대로 맡긴다.
 */
@Component
public class PointFacade {

    private static final int MAX_KEY_LENGTH = 200;

    private final PointService pointService;
    private final IdempotencyService idempotencyService;

    public PointFacade(PointService pointService, IdempotencyService idempotencyService) {
        this.pointService = pointService;
        this.idempotencyService = idempotencyService;
    }

    public EarnResult earn(String key, EarnRequest r) {
        String fp = fingerprint("EARN", r.userId(), norm(r.amount()), r.expiryDays());
        return execute(key, "EARN", fp, EarnResult.class,
                () -> pointService.earn(r.userId(), r.amount(), r.expiryDays()));
    }

    public EarnResult manualEarn(String key, EarnRequest r) {
        String fp = fingerprint("MANUAL_EARN", r.userId(), norm(r.amount()), r.expiryDays());
        return execute(key, "MANUAL_EARN", fp, EarnResult.class,
                () -> pointService.manualEarn(r.userId(), r.amount(), r.expiryDays()));
    }

    public CancelEarnResult cancelEarn(String key, Long transactionId) {
        String fp = fingerprint("CANCEL_EARN", transactionId);
        return execute(key, "CANCEL_EARN", fp, CancelEarnResult.class,
                () -> pointService.cancelEarn(transactionId));
    }

    public UseResult use(String key, UseRequest r) {
        String fp = fingerprint("USE", r.userId(), r.orderId(), norm(r.amount()));
        return execute(key, "USE", fp, UseResult.class,
                () -> pointService.use(r.userId(), r.orderId(), r.amount()));
    }

    public CancelUseResult cancelUse(String key, Long useTransactionId, CancelUseRequest r) {
        String fp = fingerprint("CANCEL_USE", useTransactionId, norm(r.amount()));
        return execute(key, "CANCEL_USE", fp, CancelUseResult.class,
                () -> pointService.cancelUse(useTransactionId, r.amount()));
    }

    /** 요청 지문: 연산명과 파라미터를 '|'로 이어 붙인다. 같은 키·다른 요청 오용 탐지용. */
    private static String fingerprint(String operation, Object... params) {
        StringBuilder sb = new StringBuilder(operation);
        for (Object p : params) {
            sb.append('|').append(p);
        }
        return sb.toString();
    }

    /** BigDecimal 스케일 차이(1000 vs 1000.00)로 동일 요청이 다르게 취급되지 않도록 정규화. */
    private static String norm(BigDecimal amount) {
        return amount == null ? "null" : amount.stripTrailingZeros().toPlainString();
    }

    /** 멱등키를 검증하고 멱등 실행을 IdempotencyService에 위임한다. */
    private <T> T execute(String key, String operation, String fingerprint,
                          Class<T> type, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            throw new PointExceptions.MissingIdempotencyKey(
                    "Idempotency-Key 헤더는 필수입니다.");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new PointExceptions.MissingIdempotencyKey(
                    "Idempotency-Key는 최대 " + MAX_KEY_LENGTH + "자입니다.");
        }
        return idempotencyService.runOnce(key, operation, fingerprint, type, action);
    }
}
