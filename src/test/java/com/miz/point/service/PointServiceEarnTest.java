package com.miz.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miz.point.domain.Point;
import com.miz.point.domain.PointStatus;
import com.miz.point.domain.SourceType;
import com.miz.point.exception.PointExceptions;
import com.miz.point.repository.PointRepository;
import com.miz.point.service.PointResults.EarnResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적립·적립취소 서비스 단위 동작 검증. H2 실 DB에 대해 실제 리포지토리로 검증한다.
 * 각 테스트는 트랜잭션 롤백으로 격리된다.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "point.max-per-earn=100000",
        "point.max-balance=5000000",
        "point.default-expiry-days=365"
})
class PointServiceEarnTest {

    @Autowired
    PointService pointService;

    @Autowired
    PointRepository pointRepository;

    private static final String USER = "user-earn";

    @Test
    void 적립하면_포인트와_잔액이_증가한다() {
        EarnResult result = pointService.earn(USER, new BigDecimal("1000"), null);

        assertThat(result.transactionId()).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo("1000");
        assertThat(result.balance()).isEqualByComparingTo("1000");

        Point point = pointRepository.findByTransactionId(result.transactionId()).orElseThrow();
        assertThat(point.getSourceType()).isEqualTo(SourceType.EARN);
        assertThat(point.getRemainingAmount()).isEqualByComparingTo("1000");
        assertThat(point.getStatus()).isEqualTo(PointStatus.AVAILABLE);
    }

    @Test
    void 기본_만료일은_365일이다() {
        EarnResult result = pointService.earn(USER, new BigDecimal("1000"), null);
        Point point = pointRepository.findByTransactionId(result.transactionId()).orElseThrow();
        long days = java.time.Duration.between(point.getEarnedAt(), point.getExpiresAt()).toDays();
        assertThat(days).isEqualTo(365);
    }

    @Test
    void 적립금액이_0이하이면_거부한다() {
        assertThatThrownBy(() -> pointService.earn(USER, BigDecimal.ZERO, null))
                .isInstanceOf(PointExceptions.InvalidAmount.class);
    }

    @Test
    void 적립금액이_최대치를_초과하면_거부한다() {
        assertThatThrownBy(() -> pointService.earn(USER, new BigDecimal("100001"), null))
                .isInstanceOf(PointExceptions.InvalidAmount.class);
    }

    @Test
    void 적립금액이_정확히_최대치면_허용한다() {
        EarnResult result = pointService.earn("user-max-earn", new BigDecimal("100000"), null);
        assertThat(result.amount()).isEqualByComparingTo("100000");
    }

    @Test
    void 최대잔액을_초과하는_적립은_거부한다() {
        String u = "user-cap";
        pointService.earn(u, new BigDecimal("100000"), null);
        // 이미 10만, 최대잔액 500만 → 대량 적립 반복 후 초과 시도
        for (int i = 0; i < 49; i++) {
            pointService.earn(u, new BigDecimal("100000"), null);
        }
        // 총 500만 도달. 1 추가 시 초과
        assertThatThrownBy(() -> pointService.earn(u, new BigDecimal("1"), null))
                .isInstanceOf(PointExceptions.MaxBalanceExceeded.class);
    }

    @Test
    void 만료일이_최소미만이면_거부한다() {
        assertThatThrownBy(() -> pointService.earn(USER, new BigDecimal("1000"), 0))
                .isInstanceOf(PointExceptions.InvalidExpiry.class);
    }

    @Test
    void 만료일이_5년이상이면_거부한다() {
        assertThatThrownBy(() -> pointService.earn(USER, new BigDecimal("1000"), 365 * 5 + 1))
                .isInstanceOf(PointExceptions.InvalidExpiry.class);
    }

    @Test
    void 수기지급은_MANUAL로_구분된다() {
        EarnResult result = pointService.manualEarn("user-manual", new BigDecimal("1000"), null);
        Point point = pointRepository.findByTransactionId(result.transactionId()).orElseThrow();
        assertThat(point.getSourceType()).isEqualTo(SourceType.MANUAL);
    }

    @Test
    void 미사용_적립은_취소할_수_있다() {
        EarnResult earned = pointService.earn("user-cancel", new BigDecimal("1000"), null);

        var cancel = pointService.cancelEarn(earned.transactionId());

        assertThat(cancel.balance()).isEqualByComparingTo("0");
        Point point = pointRepository.findByTransactionId(earned.transactionId()).orElseThrow();
        assertThat(point.getStatus()).isEqualTo(PointStatus.CANCELLED);
        assertThat(point.getRemainingAmount()).isEqualByComparingTo("0");
    }

    @Test
    void 존재하지_않는_적립_취소는_거부한다() {
        assertThatThrownBy(() -> pointService.cancelEarn(999999L))
                .isInstanceOf(PointExceptions.NotFound.class);
    }
}
