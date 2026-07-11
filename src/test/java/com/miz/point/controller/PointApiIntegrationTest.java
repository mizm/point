package com.miz.point.controller;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miz.point.support.MutableClock;
import com.miz.point.support.TestClockConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * REST API end-to-end 통합테스트. HELP.md 워크드 예시(A~E)를 API 호출로 재현한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class PointApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MutableClock clock;

    @Test
    void HELP_예시를_API로_재현한다() throws Exception {
        String user = "api-example";

        // 1. A: 1000 적립 (30일 만료)
        long aKey = earn(user, "1000", 30);
        // 2. B: 500 적립 (365일 만료)
        clock.advanceDays(1);
        long bKey = earn(user, "500", 365);

        // 3. C: 주문 A1234에서 1200 사용
        MvcResult useResult = mockMvc.perform(post("/api/points/use")
                        .header("Idempotency-Key", "use-A1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","orderId":"A1234","amount":1200}
                                """.formatted(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(300)))
                .andReturn();
        long cKey = objectMapper.readTree(useResult.getResponse().getContentAsString()).get("transactionId").asLong();

        // 4. A 만료
        clock.advanceDays(40);

        // 5. D: C의 1200 중 1100 사용취소
        MvcResult cancelResult = mockMvc.perform(post("/api/points/use/" + cKey + "/cancel")
                        .header("Idempotency-Key", "cancel-1100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(1400)))
                .andReturn();

        // A는 만료 → E로 재적립(reGranted=true, 1000), B는 복원
        JsonNode restored = objectMapper.readTree(cancelResult.getResponse().getContentAsString()).get("restored");
        boolean hasRegrant = false;
        for (JsonNode line : restored) {
            if (line.get("reGranted").asBoolean()) {
                hasRegrant = true;
                org.assertj.core.api.Assertions.assertThat(new BigDecimal(line.get("amount").asText()))
                        .isEqualByComparingTo("1000");
                org.assertj.core.api.Assertions.assertThat(line.get("newTransactionId").asLong()).isPositive();
            }
        }
        org.assertj.core.api.Assertions.assertThat(hasRegrant).isTrue();

        // 최종 잔액 1400 재확인
        mockMvc.perform(get("/api/points/balance").param("userId", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(1400)));

        // C는 이제 100만 취소 가능: 101 → 409
        mockMvc.perform(post("/api/points/use/" + cKey + "/cancel")
                        .header("Idempotency-Key", "cancel-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":101}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_AMOUNT_EXCEEDED"));

        // 정확히 100 → 성공
        mockMvc.perform(post("/api/points/use/" + cKey + "/cancel")
                        .header("Idempotency-Key", "cancel-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void 적립금액_초과는_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "bad-amount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"api-bad","amount":100001}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
    }

    @Test
    void 멱등키_없는_변경요청은_400() throws Exception {
        mockMvc.perform(post("/api/points/earn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"no-key","amount":1000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }

    private long earn(String user, String amount, int expiryDays) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "earn-" + user + "-" + amount + "-" + expiryDays + "-" + java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","amount":%s,"expiryDays":%d}
                                """.formatted(user, amount, expiryDays)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("transactionId").asLong();
    }
}
