package com.miz.point.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 적립/수기지급 요청. expiryDays가 null이면 기본 만료일을 적용한다.
 */
public record EarnRequest(
        @NotBlank String userId,
        @NotNull @Positive BigDecimal amount,
        Integer expiryDays) {
}
