package com.miz.point.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 테스트에서 시간을 임의로 조정할 수 있는 Clock. 만료 시나리오 검증에 사용한다.
 */
public class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    /** 현재 시각을 지정 일수만큼 앞으로 이동. */
    public void advanceDays(long days) {
        this.instant = this.instant.plus(java.time.Duration.ofDays(days));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
