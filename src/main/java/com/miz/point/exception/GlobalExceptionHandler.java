package com.miz.point.exception;

import com.miz.point.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 포인트 도메인 예외를 HTTP 응답으로 매핑한다.
 * 검증/금액/만료 → 400, 잔액부족/최대잔액/이미사용/취소초과 → 409, not-found → 404.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            PointExceptions.InvalidAmount.class,
            PointExceptions.InvalidExpiry.class,
            PointExceptions.MissingIdempotencyKey.class,
            PointExceptions.MissingOrderId.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(PointException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler({
            PointExceptions.MaxBalanceExceeded.class,
            PointExceptions.InsufficientBalance.class,
            PointExceptions.EarnAlreadyUsed.class,
            PointExceptions.CancelAmountExceeded.class,
            PointExceptions.IdempotencyKeyReused.class
    })
    public ResponseEntity<ErrorResponse> handleConflict(PointException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(PointExceptions.NotFound.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PointException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }
}
