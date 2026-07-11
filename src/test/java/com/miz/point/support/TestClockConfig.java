package com.miz.point.support;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 고정 시작 시각의 MutableClock을 Clock 빈으로 주입한다.
 * @Primary로 프로덕션 ClockConfig의 clock 빈보다 우선한다.
 */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
}
