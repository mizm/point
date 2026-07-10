package com.miz.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 멱등성 레코드. 클라이언트가 보낸 Idempotency-Key를 PK로, 첫 성공 응답을 JSON으로
 * 저장한다. 같은 키로 재요청이 오면 저장된 응답을 그대로 돌려주어 이중 적용을 막는다.
 * 어떤 엔티티와도 FK 관계가 없는 독립 사이드카 테이블이다.
 */
@Entity
@Table(name = "idempotency_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord implements Persistable<String> {

    /** 클라이언트가 보낸 멱등키 */
    @Id
    @Column(length = 200)
    private String idempotencyKey;

    /** 어떤 API였는지 (EARN, MANUAL_EARN, CANCEL_EARN, USE, CANCEL_USE) */
    @Column(nullable = false, length = 40)
    private String operation;

    /** 요청 파라미터 지문. 같은 키·다른 요청 오용 탐지용. */
    @Column(nullable = false, length = 512)
    private String requestHash;

    /** 첫 성공 응답 직렬화(JSON) */
    @Lob
    @Column(nullable = false)
    private String responseBody;

    @Column(nullable = false)
    private Instant createdAt;

    private IdempotencyRecord(String idempotencyKey, String operation, String requestHash,
                              String responseBody, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public static IdempotencyRecord of(String idempotencyKey, String operation, String requestHash,
                                       String responseBody, Instant createdAt) {
        return new IdempotencyRecord(idempotencyKey, operation, requestHash, responseBody, createdAt);
    }

    /** Persistable 인터페이스 구현용. 도메인 접근자는 getIdempotencyKey(). */
    @Override
    public String getId() {
        return idempotencyKey;
    }

    /**
     * 항상 true. 멱등성 레코드는 최초 생성 시 1회만 저장되고 절대 갱신되지 않으므로,
     * Spring Data가 매 저장을 INSERT(persist)로 처리하도록 강제한다. 조회로 얻은
     * (detached) 레코드를 다시 save()에 넘기면 안 된다.
     */
    @Override
    public boolean isNew() {
        return true;
    }
}
