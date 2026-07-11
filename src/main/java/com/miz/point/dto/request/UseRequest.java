package com.miz.point.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 포인트 사용 요청. 주문번호를 함께 기록한다.
 */
public record UseRequest(
        @NotBlank String userId,
        @NotBlank String orderId,
        @NotNull @Positive BigDecimal amount) {
}
