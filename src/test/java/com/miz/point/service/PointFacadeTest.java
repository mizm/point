package com.miz.point.service;

import com.miz.point.dto.request.EarnRequest;
import com.miz.point.exception.PointExceptions;
import com.miz.point.service.PointResults.EarnResult;
import java.math.BigDecimal;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * PointFacade는 멱등키 검증과 위임만 담당한다(재시도/경쟁 해소는 IdempotencyService).
 */
class PointFacadeTest {

    private final EarnRequest request = new EarnRequest("u", new BigDecimal("1000"), null);

    @Test
    void 멱등키가_없으면_400_예외() {
        PointFacade facade = new PointFacade(Mockito.mock(PointService.class),
                Mockito.mock(IdempotencyService.class));

        Assertions.assertThatThrownBy(() -> facade.earn("  ", request))
                .isInstanceOf(PointExceptions.MissingIdempotencyKey.class);
    }

    @Test
    void 멱등키가_너무_길면_400_예외() {
        PointFacade facade = new PointFacade(Mockito.mock(PointService.class),
                Mockito.mock(IdempotencyService.class));

        Assertions.assertThatThrownBy(() -> facade.earn("x".repeat(201), request))
                .isInstanceOf(PointExceptions.MissingIdempotencyKey.class);
    }

    @Test
    void 유효한_키는_runOnce에_위임한다() {
        IdempotencyService idem = Mockito.mock(IdempotencyService.class);
        EarnResult expected = new EarnResult(1L, new BigDecimal("1000"), Instant.now(), new BigDecimal("1000"));
        Mockito.when(idem.runOnce(ArgumentMatchers.eq("k"), ArgumentMatchers.eq("EARN"),
                        ArgumentMatchers.anyString(), ArgumentMatchers.eq(EarnResult.class),
                        ArgumentMatchers.any()))
                .thenReturn(expected);

        PointFacade facade = new PointFacade(Mockito.mock(PointService.class), idem);

        Assertions.assertThat(facade.earn("k", request)).isSameAs(expected);
        Mockito.verify(idem, Mockito.times(1)).runOnce(ArgumentMatchers.eq("k"),
                ArgumentMatchers.eq("EARN"), ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(EarnResult.class), ArgumentMatchers.any());
    }
}
