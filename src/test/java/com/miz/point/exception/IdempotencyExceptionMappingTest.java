package com.miz.point.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.miz.point.dto.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class IdempotencyExceptionMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 멱등키_누락은_400_MISSING_IDEMPOTENCY_KEY() {
        ResponseEntity<ErrorResponse> res =
                handler.handleBadRequest(new PointExceptions.MissingIdempotencyKey("no key"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().code()).isEqualTo("MISSING_IDEMPOTENCY_KEY");
    }

    @Test
    void 멱등키_재사용은_409_IDEMPOTENCY_KEY_REUSED() {
        ResponseEntity<ErrorResponse> res =
                handler.handleConflict(new PointExceptions.IdempotencyKeyReused("reused"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }
}
