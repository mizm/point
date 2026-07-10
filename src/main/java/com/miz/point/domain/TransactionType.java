package com.miz.point.domain;

/**
 * 포인트 원장(append-only)에 기록되는 행위 유형.
 */
public enum TransactionType {
    /** 적립 (일반/수기지급 공통) */
    EARN,
    /** 적립취소 */
    CANCEL_EARN,
    /** 사용 */
    USE,
    /** 사용취소 */
    CANCEL_USE,
    /** 만료 포인트 사용취소에 따른 신규 복원 적립 */
    RESTORE
}
