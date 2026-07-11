package com.miz.point.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miz.point.dto.request.EarnRequest;
import com.miz.point.service.PointResults.EarnResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 진짜 동시(concurrent) 중복 요청 검증. 여러 스레드가 같은 멱등키로 동시에 적립을
 * 요청해도 딱 1회만 반영되어야 한다. runOnce의 findById 재사용만으로는 못 막는
 * 경쟁 구간(둘 다 "없음"으로 판단해 진입)을 DB 유니크 제약 + 파사드 재시도가
 * 어떻게 막는지 실제로 확인한다.
 *
 * <p>실제 커밋이 발생해야 경쟁이 재현되므로 클래스에 @Transactional을 걸지 않는다.
 */
@SpringBootTest
class IdempotencyConcurrencyTest {

    @Autowired
    PointFacade pointFacade;

    @Autowired
    PointService pointService;

    @Test
    void 같은_키로_동시에_적립해도_1회만_반영된다() throws Exception {
        String userId = "concurrent-earn";
        String key = "concurrent-key-1";
        int threads = 16;
        EarnRequest request = new EarnRequest(userId, new BigDecimal("1000"), null);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<EarnResult> results = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    ready.countDown();
                    start.await();                    // 모든 스레드가 동시에 출발
                    try {
                        results.add(pointFacade.earn(key, request));
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                    return null;
                }));
            }

            ready.await(5, TimeUnit.SECONDS);         // 전 스레드 준비 대기
            start.countDown();                        // 일제히 출발
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // 어떤 요청도 예외로 실패하지 않아야 한다 (진 쪽은 저장된 응답으로 복구).
        assertThat(failures).isEmpty();
        // 모든 스레드가 동일한 응답(같은 transactionId)을 받아야 한다.
        assertThat(results).hasSize(threads);
        long distinctPointKeys = results.stream().map(EarnResult::transactionId).distinct().count();
        assertThat(distinctPointKeys).isEqualTo(1);
        // 잔액은 16번이 아니라 1번만 적립되어 정확히 1000이어야 한다.
        assertThat(pointService.getBalance(userId)).isEqualByComparingTo("1000");
    }
}
