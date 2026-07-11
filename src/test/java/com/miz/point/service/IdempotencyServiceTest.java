package com.miz.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miz.point.exception.PointExceptions;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * IdempotencyService 오케스트레이션 검증. 실제 저장/조회는 IdempotencyAttempt에 위임되며,
 * 이 서비스는 재사용·재시도·전파 정책을 담당한다. 정책 분기는 Mockito로, 실제 DB 왕복은
 * @SpringBootTest 빈으로 검증한다.
 */
@SpringBootTest
@Transactional
class IdempotencyServiceTest {

    @Autowired
    IdempotencyService idempotencyService;

    /** 테스트용 응답 타입 (record → Jackson 직렬화/역직렬화). */
    record Sample(String name, int value) {
    }

    @Test
    void 첫_호출은_action을_실행하고_결과를_반환한다() {
        AtomicInteger calls = new AtomicInteger();

        Sample result = idempotencyService.runOnce("k1", "OP", "fp", Sample.class,
                () -> {
                    calls.incrementAndGet();
                    return new Sample("a", 1);
                });

        assertThat(result).isEqualTo(new Sample("a", 1));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void 같은_키_재호출은_action을_다시_실행하지_않고_저장된_결과를_반환한다() {
        AtomicInteger calls = new AtomicInteger();
        idempotencyService.runOnce("k2", "OP", "fp", Sample.class,
                () -> { calls.incrementAndGet(); return new Sample("a", 1); });

        Sample second = idempotencyService.runOnce("k2", "OP", "fp", Sample.class,
                () -> { calls.incrementAndGet(); return new Sample("b", 2); });

        assertThat(second).isEqualTo(new Sample("a", 1)); // 첫 결과 그대로
        assertThat(calls.get()).isEqualTo(1);             // 두 번째 action 미실행
    }

    @Test
    void 같은_키_다른_요청지문은_재사용_예외() {
        idempotencyService.runOnce("k3", "OP", "fp-A", Sample.class,
                () -> new Sample("a", 1));

        assertThatThrownBy(() -> idempotencyService.runOnce("k3", "OP", "fp-B", Sample.class,
                () -> new Sample("b", 2)))
                .isInstanceOf(PointExceptions.IdempotencyKeyReused.class);
    }

    @Test
    void 멱등키_충돌이면_재조회로_저장된_응답을_반환한다() {
        IdempotencyAttempt attempt = Mockito.mock(IdempotencyAttempt.class);
        // 1회차 run은 유니크 충돌(동시 요청의 패자), exists=true(승자 커밋됨), 2회차 run은 저장된 응답.
        Mockito.when(attempt.run(ArgumentMatchers.eq("dup"), ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString(), ArgumentMatchers.<Class<Sample>>any(),
                        ArgumentMatchers.any()))
                .thenThrow(new DataIntegrityViolationException("dup key"))
                .thenReturn(new Sample("winner", 9));
        Mockito.when(attempt.exists("dup")).thenReturn(true);

        IdempotencyService svc = new IdempotencyService(attempt);

        Sample result = svc.runOnce("dup", "OP", "fp", Sample.class, () -> new Sample("loser", 1));

        assertThat(result).isEqualTo(new Sample("winner", 9));
        Mockito.verify(attempt, Mockito.times(2)).run(ArgumentMatchers.eq("dup"),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.<Class<Sample>>any(), ArgumentMatchers.any());
    }

    @Test
    void 멱등키_충돌이_아니면_예외를_전파한다() {
        IdempotencyAttempt attempt = Mockito.mock(IdempotencyAttempt.class);
        // run이 비즈니스 제약 위반으로 실패하고, 키는 존재하지 않음 → 재시도 없이 전파.
        Mockito.when(attempt.run(ArgumentMatchers.eq("dup"), ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString(), ArgumentMatchers.<Class<Sample>>any(),
                        ArgumentMatchers.any()))
                .thenThrow(new DataIntegrityViolationException("business fk"));
        Mockito.when(attempt.exists("dup")).thenReturn(false);

        IdempotencyService svc = new IdempotencyService(attempt);

        assertThatThrownBy(() ->
                svc.runOnce("dup", "OP", "fp", Sample.class, () -> new Sample("a", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // run은 딱 1회만 (재시도 없음)
        Mockito.verify(attempt, Mockito.times(1)).run(ArgumentMatchers.eq("dup"),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.<Class<Sample>>any(), ArgumentMatchers.any());
    }
}
