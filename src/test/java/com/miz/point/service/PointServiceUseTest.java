package com.miz.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miz.point.domain.Point;
import com.miz.point.domain.PointStatus;
import com.miz.point.domain.SourceType;
import com.miz.point.exception.PointExceptions;
import com.miz.point.repository.PointRepository;
import com.miz.point.service.PointResults.CancelUseResult;
import com.miz.point.service.PointResults.EarnResult;
import com.miz.point.service.PointResults.RestoreLine;
import com.miz.point.service.PointResults.UseResult;
import com.miz.point.support.MutableClock;
import com.miz.point.support.TestClockConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용·사용취소의 핵심 규칙 검증: 소진 우선순위, 만료 처리, 사용취소 복원 및 만료
 * 재적립. HELP.md 워크드 예시(A~E)를 포함한다.
 */
@SpringBootTest
@Transactional
@Import(TestClockConfig.class)
@TestPropertySource(properties = {
        "point.max-per-earn=100000",
        "point.max-balance=5000000",
        "point.default-expiry-days=365"
})
class PointServiceUseTest {

    @Autowired
    PointService pointService;

    @Autowired
    PointRepository pointRepository;

    @Autowired
    MutableClock clock; // testClock 빈 (타입으로 주입)

    @Test
    void 두_적립을_만료임박순_FIFO로_소진한다() {
        String u = "u-fifo";
        EarnResult a = pointService.earn(u, new BigDecimal("1000"), null); // 먼저 적립 → 먼저 만료
        clock.advanceDays(1);
        EarnResult b = pointService.earn(u, new BigDecimal("500"), null);

        UseResult use = pointService.use(u, "A1234", new BigDecimal("1200"));

        assertThat(use.balance()).isEqualByComparingTo("300");
        assertThat(use.breakdown()).containsExactly(
                new PointResults.UsageLine(a.transactionId(), new BigDecimal("1000")),
                new PointResults.UsageLine(b.transactionId(), new BigDecimal("200")));
        assertThat(pointRepository.findByTransactionId(a.transactionId()).orElseThrow().getStatus())
                .isEqualTo(PointStatus.EXHAUSTED);
        assertThat(pointRepository.findByTransactionId(b.transactionId()).orElseThrow().getRemainingAmount())
                .isEqualByComparingTo("300");
    }

    @Test
    void 수기지급이_일반적립보다_먼저_소진된다() {
        String u = "u-manual-first";
        // 일반 적립이 먼저(만료 임박)여도 수기지급이 우선
        EarnResult normal = pointService.earn(u, new BigDecimal("1000"), null);
        EarnResult manual = pointService.manualEarn(u, new BigDecimal("500"), null);

        UseResult use = pointService.use(u, "O1", new BigDecimal("500"));

        assertThat(use.breakdown()).containsExactly(
                new PointResults.UsageLine(manual.transactionId(), new BigDecimal("500")));
        assertThat(pointRepository.findByTransactionId(normal.transactionId()).orElseThrow().getRemainingAmount())
                .isEqualByComparingTo("1000");
    }

    @Test
    void 만료된_포인트는_사용후보에서_제외된다() {
        String u = "u-expired-skip";
        pointService.earn(u, new BigDecimal("1000"), 10); // 10일 후 만료
        clock.advanceDays(11);
        pointService.earn(u, new BigDecimal("500"), 30);

        assertThatThrownBy(() -> pointService.use(u, "O1", new BigDecimal("600")))
                .isInstanceOf(PointExceptions.InsufficientBalance.class);
    }

    @Test
    void 잔액부족이면_사용을_거부한다() {
        String u = "u-insufficient";
        pointService.earn(u, new BigDecimal("100"), null);
        assertThatThrownBy(() -> pointService.use(u, "O1", new BigDecimal("200")))
                .isInstanceOf(PointExceptions.InsufficientBalance.class);
    }

    @Test
    void 주문번호가_없으면_MissingOrderId로_거부한다() {
        String u = "u-no-order";
        pointService.earn(u, new BigDecimal("1000"), null);
        assertThatThrownBy(() -> pointService.use(u, " ", new BigDecimal("100")))
                .isInstanceOf(PointExceptions.MissingOrderId.class);
    }

    @Test
    void 사용취소_취소가능액을_초과하면_거부한다() {
        String u = "u-cancel-over";
        pointService.earn(u, new BigDecimal("1000"), null);
        UseResult use = pointService.use(u, "O1", new BigDecimal("500"));
        assertThatThrownBy(() -> pointService.cancelUse(use.transactionId(), new BigDecimal("600")))
                .isInstanceOf(PointExceptions.CancelAmountExceeded.class);
    }

    @Test
    void 만료전_사용취소는_원래_포인트에_복원된다() {
        String u = "u-restore-live";
        EarnResult a = pointService.earn(u, new BigDecimal("1000"), null);
        UseResult use = pointService.use(u, "O1", new BigDecimal("600"));

        CancelUseResult cancel = pointService.cancelUse(use.transactionId(), new BigDecimal("400"));

        assertThat(cancel.balance()).isEqualByComparingTo("800"); // 400 남았다가 400 복원
        RestoreLine line = cancel.restored().get(0);
        assertThat(line.reGranted()).isFalse();
        assertThat(pointRepository.findByTransactionId(a.transactionId()).orElseThrow().getRemainingAmount())
                .isEqualByComparingTo("800");
    }

    /**
     * HELP.md 워크드 예시 재현: A(1000), B(500) 적립 → C 사용 1200 →
     * A 만료 → D 사용취소 1100 → A는 만료라 E로 1000 신규적립, B는 100 복원.
     */
    @Test
    void HELP_예시_전체_시나리오() {
        String u = "u-example";
        EarnResult a = pointService.earn(u, new BigDecimal("1000"), 30);   // A, 30일 후 만료
        clock.advanceDays(1);
        EarnResult b = pointService.earn(u, new BigDecimal("500"), 365);   // B, 넉넉한 만료
        UseResult c = pointService.use(u, "A1234", new BigDecimal("1200")); // C

        assertThat(c.balance()).isEqualByComparingTo("300");

        clock.advanceDays(40); // A 만료 (30일 만료 지남), B는 아직 유효

        CancelUseResult d = pointService.cancelUse(c.transactionId(), new BigDecimal("1100")); // D

        // 최종 잔액 1400 (B 400 + E 1000)
        assertThat(d.balance()).isEqualByComparingTo("1400");

        // B는 300 → 400 복원 (만료 전)
        assertThat(pointRepository.findByTransactionId(b.transactionId()).orElseThrow().getRemainingAmount())
                .isEqualByComparingTo("400");

        // A는 만료 → 복원 대신 신규 RESTORE 포인트 E(1000) 생성
        List<RestoreLine> restored = d.restored();
        RestoreLine aLine = restored.stream().filter(RestoreLine::reGranted).findFirst().orElseThrow();
        assertThat(aLine.amount()).isEqualByComparingTo("1000");
        Point e = pointRepository.findByTransactionId(aLine.newTransactionId()).orElseThrow();
        assertThat(e.getSourceType()).isEqualTo(SourceType.RESTORE);
        assertThat(e.getRemainingAmount()).isEqualByComparingTo("1000");

        // A 원본은 그대로 소진 상태 유지 (건드리지 않음)
        assertThat(pointRepository.findByTransactionId(a.transactionId()).orElseThrow().getRemainingAmount())
                .isEqualByComparingTo("0");

        // C는 이제 100만 추가 취소 가능
        assertThatThrownBy(() -> pointService.cancelUse(c.transactionId(), new BigDecimal("101")))
                .isInstanceOf(PointExceptions.CancelAmountExceeded.class);
        CancelUseResult last = pointService.cancelUse(c.transactionId(), new BigDecimal("100"));
        assertThat(last.amount()).isEqualByComparingTo("100");
    }
}
