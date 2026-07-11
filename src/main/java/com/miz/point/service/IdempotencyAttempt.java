package com.miz.point.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miz.point.domain.IdempotencyRecord;
import com.miz.point.exception.PointExceptions;
import com.miz.point.repository.IdempotencyRecordRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멱등성의 트랜잭션 단위. 각 메서드가 독립 트랜잭션으로 실행되도록 오케스트레이터
 * ({@link IdempotencyService})와 별도 빈으로 분리한다 — 같은 클래스 내 self-invocation은
 * 프록시를 우회해 트랜잭션 경계가 생기지 않기 때문이다.
 *
 * <p>{@link #run}에서 저장이 유니크 제약을 위반하면 그 트랜잭션은 rollback-only가 되므로,
 * 재시도는 반드시 이 트랜잭션 밖(오케스트레이터)에서 새 호출로 이뤄져야 한다.
 */
@Component
class IdempotencyAttempt {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    IdempotencyAttempt(IdempotencyRecordRepository repository,
                       ObjectMapper objectMapper,
                       Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 멱등키로 저장된 응답이 있으면 재사용하고, 없으면 action을 실행해 그 성공 응답을
     * 저장한다. action(비즈니스 로직)과 레코드 저장이 한 트랜잭션에 묶이므로, 저장 시
     * 유니크 제약을 위반하면 비즈니스 쓰기까지 함께 롤백된다.
     */
    @Transactional
    public <T> T run(String key, String operation, String requestHash,
                     Class<T> responseType, Supplier<T> action) {
        Optional<IdempotencyRecord> existing = repository.findById(key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new PointExceptions.IdempotencyKeyReused(
                        "같은 멱등키로 다른 요청이 접수되었습니다: " + key);
            }
            return deserialize(record.getResponseBody(), responseType);
        }

        T result = action.get();
        repository.saveAndFlush(IdempotencyRecord.of(
                key, operation, requestHash, serialize(result), clock.instant()));
        return result;
    }

    /** 해당 멱등키의 레코드가 이미 커밋되어 존재하는지 여부. */
    @Transactional(readOnly = true)
    public boolean exists(String key) {
        return repository.existsById(key);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("응답 직렬화 실패", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("응답 역직렬화 실패", e);
        }
    }
}
