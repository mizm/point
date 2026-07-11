package com.miz.point.controller;

import com.miz.point.dto.request.CancelUseRequest;
import com.miz.point.dto.request.EarnRequest;
import com.miz.point.dto.request.UseRequest;
import com.miz.point.service.PointFacade;
import com.miz.point.service.PointResults.CancelEarnResult;
import com.miz.point.service.PointResults.CancelUseResult;
import com.miz.point.service.PointResults.EarnResult;
import com.miz.point.service.PointResults.UseResult;
import com.miz.point.service.PointService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 API. 적립/수기지급/적립취소/사용/사용취소/잔액조회.
 * 모든 변경 API는 Idempotency-Key 헤더로 멱등하게 처리된다(파사드 경유).
 */
@RestController
@RequestMapping("/api/points")
public class PointController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PointFacade pointFacade;
    private final PointService pointService;

    public PointController(PointFacade pointFacade, PointService pointService) {
        this.pointFacade = pointFacade;
        this.pointService = pointService;
    }

    /** 적립 */
    @PostMapping("/earn")
    public EarnResult earn(@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                           @Valid @RequestBody EarnRequest request) {
        return pointFacade.earn(key, request);
    }

    /** 관리자 수기지급 */
    @PostMapping("/admin/manual-earn")
    public EarnResult manualEarn(@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                                 @Valid @RequestBody EarnRequest request) {
        return pointFacade.manualEarn(key, request);
    }

    /** 적립취소 */
    @PostMapping("/earn/{pointKey}/cancel")
    public CancelEarnResult cancelEarn(@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                                       @PathVariable("pointKey") Long transactionId) {
        return pointFacade.cancelEarn(key, transactionId);
    }

    /** 사용 */
    @PostMapping("/use")
    public UseResult use(@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                         @Valid @RequestBody UseRequest request) {
        return pointFacade.use(key, request);
    }

    /** 사용취소 */
    @PostMapping("/use/{usePointKey}/cancel")
    public CancelUseResult cancelUse(@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                                     @PathVariable("usePointKey") Long useTransactionId,
                                     @Valid @RequestBody CancelUseRequest request) {
        return pointFacade.cancelUse(key, useTransactionId, request);
    }

    /** 잔액조회 (조회는 멱등키 불필요) */
    @GetMapping("/balance")
    public Map<String, Object> balance(@RequestParam String userId) {
        BigDecimal balance = pointService.getBalance(userId);
        return Map.of("userId", userId, "balance", balance);
    }
}
