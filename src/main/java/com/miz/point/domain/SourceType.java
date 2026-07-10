package com.miz.point.domain;

/**
 * 포인트가 적립된 경로. 명세 3.1.4의 "수기지급 포인트는 다른 적립과 구분" 요구사항을
 * 이 값으로 식별한다.
 */
public enum SourceType {
    /** 일반 적립 */
    EARN,
    /** 관리자 수기지급 (사용 시 우선 소진) */
    MANUAL,
    /** 만료된 포인트를 사용취소하면서 신규 적립된 복원 포인트 */
    RESTORE
}
