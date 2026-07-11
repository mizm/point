package com.miz.point.dto.response;

/**
 * 표준 에러 응답 본문.
 */
public record ErrorResponse(String code, String message) {
}
