package com.miz.point.exception;

/**
 * 포인트 도메인 예외 모음. 각 예외는 코드와 함께 HTTP 매핑의 근거가 된다.
 */
public final class PointExceptions {

    private PointExceptions() {
    }

    /** 적립 금액이 허용 범위를 벗어남 (400) */
    public static class InvalidAmount extends PointException {
        public InvalidAmount(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "INVALID_AMOUNT";
        }
    }

    /** 만료일이 허용 범위를 벗어남 (400) */
    public static class InvalidExpiry extends PointException {
        public InvalidExpiry(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "INVALID_EXPIRY";
        }
    }

    /** 개인별 최대 보유 잔액 초과 (409) */
    public static class MaxBalanceExceeded extends PointException {
        public MaxBalanceExceeded(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "MAX_BALANCE_EXCEEDED";
        }
    }

    /** 가용 잔액 부족 (409) */
    public static class InsufficientBalance extends PointException {
        public InsufficientBalance(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "INSUFFICIENT_BALANCE";
        }
    }

    /** 이미 일부라도 사용된 적립은 취소 불가 (409) */
    public static class EarnAlreadyUsed extends PointException {
        public EarnAlreadyUsed(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "EARN_ALREADY_USED";
        }
    }

    /** 사용취소 금액이 취소가능액을 초과 (409) */
    public static class CancelAmountExceeded extends PointException {
        public CancelAmountExceeded(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "CANCEL_AMOUNT_EXCEEDED";
        }
    }

    /** 대상 포인트/트랜잭션을 찾을 수 없음 (404) */
    public static class NotFound extends PointException {
        public NotFound(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "NOT_FOUND";
        }
    }

    /** 변경 API에 Idempotency-Key 헤더 누락 (400) */
    public static class MissingIdempotencyKey extends PointException {
        public MissingIdempotencyKey(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "MISSING_IDEMPOTENCY_KEY";
        }
    }

    /** 같은 멱등키로 다른 파라미터 요청 (409) */
    public static class IdempotencyKeyReused extends PointException {
        public IdempotencyKeyReused(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "IDEMPOTENCY_KEY_REUSED";
        }
    }

    /** 사용 요청에 주문번호 누락 (400) */
    public static class MissingOrderId extends PointException {
        public MissingOrderId(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "MISSING_ORDER_ID";
        }
    }
}
