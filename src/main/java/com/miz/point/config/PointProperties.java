package com.miz.point.config;

import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 포인트 정책 설정값. 명세 3.1.1(1회 최대 적립)·3.1.2(개인별 최대 보유)의 "하드코딩이
 * 아닌 방법으로 제어"를 충족하기 위해 소스 상수가 아닌 외부 설정(application.properties)에서
 * 주입한다. 소스 재빌드 없이 설정 파일만 수정해 값을 바꿀 수 있다(반영에 재기동 필요).
 */
@ConfigurationProperties(prefix = "point")
@Validated
public class PointProperties {

    /** 1회 적립 가능한 최대 포인트 (명세: 10만 이하) */
    @Min(1)
    private BigDecimal maxPerEarn = BigDecimal.valueOf(100_000);

    /** 개인별 보유 가능한 최대 잔액 */
    @Min(1)
    private BigDecimal maxBalance = BigDecimal.valueOf(5_000_000);

    /** 기본 만료일 (명세: 365일) */
    @Min(1)
    private int defaultExpiryDays = 365;

    /** 부여 가능한 최소 만료일 (명세: 최소 1일 이상) */
    @Min(1)
    private int minExpiryDays = 1;

    /** 부여 가능한 만료 상한 연수 (명세: 5년 미만) */
    @Min(1)
    private int maxExpiryYears = 5;

    public BigDecimal getMaxPerEarn() {
        return maxPerEarn;
    }

    public void setMaxPerEarn(BigDecimal maxPerEarn) {
        this.maxPerEarn = maxPerEarn;
    }

    public BigDecimal getMaxBalance() {
        return maxBalance;
    }

    public void setMaxBalance(BigDecimal maxBalance) {
        this.maxBalance = maxBalance;
    }

    public int getDefaultExpiryDays() {
        return defaultExpiryDays;
    }

    public void setDefaultExpiryDays(int defaultExpiryDays) {
        this.defaultExpiryDays = defaultExpiryDays;
    }

    public int getMinExpiryDays() {
        return minExpiryDays;
    }

    public void setMinExpiryDays(int minExpiryDays) {
        this.minExpiryDays = minExpiryDays;
    }

    public int getMaxExpiryYears() {
        return maxExpiryYears;
    }

    public void setMaxExpiryYears(int maxExpiryYears) {
        this.maxExpiryYears = maxExpiryYears;
    }
}
