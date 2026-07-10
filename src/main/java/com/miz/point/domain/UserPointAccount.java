package com.miz.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자별 포인트 계정. 각 변경 작업 시작 시 이 행을 비관적 락(PESSIMISTIC_WRITE)으로
 * 잠가 동시 적립의 한도 우회와 동시 사용의 이중지출을 방지하는 락 앵커 역할을 한다.
 * 실제 잔액은 Point 잔액 합계에서 파생되므로 여기서는 저장하지 않는다.
 */
@Entity
@Table(name = "user_point_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPointAccount {

    @Id
    private String userId;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    private UserPointAccount(String userId, Instant createdAt) {
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public static UserPointAccount of(String userId, Instant createdAt) {
        return new UserPointAccount(userId, createdAt);
    }
}
