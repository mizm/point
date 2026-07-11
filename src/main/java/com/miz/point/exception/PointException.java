package com.miz.point.exception;

/**
 * 포인트 도메인 예외의 기반 클래스. 각 하위 예외는 고유 코드를 가진다.
 */
public abstract class PointException extends RuntimeException {

    protected PointException(String message) {
        super(message);
    }

    /** 클라이언트에 노출할 에러 코드. */
    public abstract String code();
}
