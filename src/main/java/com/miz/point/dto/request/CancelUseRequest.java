package com.miz.point.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 사용취소 요청. 전체 또는 일부 금액을 취소한다.
 */
public record CancelUseRequest(
        @NotNull @Positive BigDecimal amount) {
}
