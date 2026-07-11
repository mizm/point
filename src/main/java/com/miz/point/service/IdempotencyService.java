package com.miz.point.service;

import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 멱등성 오케스트레이터. 멱등키 하나로 action이 딱 1회만 반영되도록 조율한다.
 * 실제 DB 작업은 트랜잭션 단위인 {@link IdempotencyAttempt}에 위임하고, 이 클래스는
 * 트랜잭션 밖에서 동시 중복 요청의 경쟁만 해소한다.
 *
 * <p><b>동시성:</b> 거의 동시에 같은 키로 들어온 두 요청은 둘 다 저장 레코드가 "없음"으로
 * 판단해 action을 실행하지만, 저장 시 유니크 제약으로 한쪽만 성공한다. 진 쪽은
 * {@link DataIntegrityViolationException}으로 트랜잭션이 롤백된 뒤, 승자가 커밋한 레코드를
 * 다시 조회해 저장된 응답을 돌려받는다. 재시도가 트랜잭션 밖에 있어야 하는 이유는
 * 실패한 트랜잭션이 rollback-only로 오염되어 재조회가 불가능하기 때문이다.
 */
@Service
public class IdempotencyService {

    private final IdempotencyAttempt attempt;

    IdempotencyService(IdempotencyAttempt attempt) {
        this.attempt = attempt;
    }

    /**
     * 멱등하게 action을 1회만 실행한다.
     *
     * @param key          멱등키
     * @param operation    API 식별자 (EARN 등)
     * @param requestHash  요청 파라미터 지문
     * @param responseType 저장된 응답을 역직렬화할 타입
     * @param action       최초 1회 실행할 비즈니스 로직
     */
    public <T> T runOnce(String key, String operation, String requestHash,
                         Class<T> responseType, Supplier<T> action) {
        try {
            return attempt.run(key, operation, requestHash, responseType, action);
        } catch (DataIntegrityViolationException race) {
            // 멱등키 유니크 충돌(동시 중복 요청)일 때만 재시도한다. 승자가 이미 레코드를
            // 커밋했으므로 키가 존재하면 재조회로 저장된 응답을 반환한다. 그 외(비즈니스
            // 제약 위반 등)는 재시도하지 않고 그대로 전파한다.
            if (attempt.exists(key)) {
                return attempt.run(key, operation, requestHash, responseType, action);
            }
            throw race;
        }
    }
}
