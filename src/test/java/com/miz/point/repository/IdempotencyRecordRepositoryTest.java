package com.miz.point.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miz.point.domain.IdempotencyRecord;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class IdempotencyRecordRepositoryTest {

    @Autowired
    IdempotencyRecordRepository repository;

    @Test
    void 저장하고_키로_조회한다() {
        repository.saveAndFlush(IdempotencyRecord.of(
                "key-1", "EARN", "EARN|u1|1000|null", "{\"balance\":1000}", Instant.now()));

        IdempotencyRecord found = repository.findById("key-1").orElseThrow();
        assertThat(found.getOperation()).isEqualTo("EARN");
        assertThat(found.getRequestHash()).isEqualTo("EARN|u1|1000|null");
        assertThat(found.getResponseBody()).isEqualTo("{\"balance\":1000}");
        assertThat(found.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 같은_키_중복_저장은_유니크제약으로_거부된다() {
        repository.saveAndFlush(IdempotencyRecord.of(
                "dup", "EARN", "fp", "{}", Instant.now()));

        assertThatThrownBy(() -> repository.saveAndFlush(IdempotencyRecord.of(
                "dup", "USE", "fp2", "{}", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
