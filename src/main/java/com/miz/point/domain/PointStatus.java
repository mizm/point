package com.miz.point.domain;

/**
 * 적립 포인트({@link Point})의 상태.
 */
public enum PointStatus {
    /** 사용 가능 (잔액 존재) */
    AVAILABLE,
    /** 잔액 소진됨 */
    EXHAUSTED,
    /** 적립취소됨 */
    CANCELLED
}
