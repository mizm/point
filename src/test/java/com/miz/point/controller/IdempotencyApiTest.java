package com.miz.point.controller;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miz.point.support.MutableClock;
import com.miz.point.support.TestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 멱등성 통합테스트. HELP.md 고려사항 1번(같은 요청 2번 호출 시 1회만 반영)을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class IdempotencyApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MutableClock clock;

    @Test
    void 같은_키로_적립_2번_호출하면_1회만_반영된다() throws Exception {
        String body = """
                {"userId":"idem-earn","amount":1000}
                """;

        MvcResult first = mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "earn-key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(1000)))
                .andReturn();
        long firstKey = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("transactionId").asLong();

        // 같은 키로 재요청 → 잔액 2000이 아니라 1000, transactionId 동일
        MvcResult second = mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "earn-key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(1000)))
                .andReturn();
        long secondKey = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("transactionId").asLong();

        org.assertj.core.api.Assertions.assertThat(secondKey).isEqualTo(firstKey);
    }

    @Test
    void 다른_키로_같은_내용_적립하면_별개로_2번_반영된다() throws Exception {
        String body = """
                {"userId":"idem-earn2","amount":1000}
                """;

        mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "diff-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(1000)));
        mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "diff-2")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(2000)));
    }

    @Test
    void 같은_키_다른_요청은_409_재사용() throws Exception {
        mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "reuse-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"idem-reuse","amount":1000}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "reuse-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"idem-reuse","amount":2000}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void 같은_키로_적립취소_2번_호출해도_1회만_취소된다() throws Exception {
        // 적립
        MvcResult earn = mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "ce-earn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"idem-cancel","amount":1000}
                                """))
                .andExpect(status().isOk()).andReturn();
        long pk = objectMapper.readTree(earn.getResponse().getContentAsString())
                .get("transactionId").asLong();

        // 적립취소 1회차
        mockMvc.perform(post("/api/points/earn/" + pk + "/cancel")
                        .header("Idempotency-Key", "ce-cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(0)));

        // 같은 키로 재취소 → 여전히 balance 0, 에러 아님 (저장된 응답 반환)
        mockMvc.perform(post("/api/points/earn/" + pk + "/cancel")
                        .header("Idempotency-Key", "ce-cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(0)));
    }

    @Test
    void 너무_긴_멱등키는_400_반환() throws Exception {
        String longKey = "x".repeat(300);
        mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", longKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"idem-long","amount":1000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    void 같은_키_스케일만_다른_금액으로_재요청하면_저장된_응답을_반환한다() throws Exception {
        // 첫 요청: amount 1000
        MvcResult first = mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "scale-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"idem-scale","amount":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(1000)))
                .andReturn();
        long firstKey = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("transactionId").asLong();

        // 재요청: amount 1000.00 (다른 스케일, 같은 값) → 저장된 응답(200) 반환, 409 아님
        MvcResult second = mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "scale-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"idem-scale","amount":1000.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(1000)))
                .andReturn();
        long secondKey = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("transactionId").asLong();

        org.assertj.core.api.Assertions.assertThat(secondKey).isEqualTo(firstKey);
    }

    @Test
    void 같은_키로_사용_2번_호출하면_1회만_사용된다() throws Exception {
        mockMvc.perform(post("/api/points/earn")
                        .header("Idempotency-Key", "use-earn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"idem-use","amount":1000}
                                """))
                .andExpect(status().isOk());

        String useBody = """
                {"userId":"idem-use","orderId":"O1","amount":600}
                """;
        MvcResult firstUse = mockMvc.perform(post("/api/points/use")
                        .header("Idempotency-Key", "use-key")
                        .contentType(MediaType.APPLICATION_JSON).content(useBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(400)))
                .andReturn();
        long firstUseKey = objectMapper.readTree(firstUse.getResponse().getContentAsString())
                .get("transactionId").asLong();

        // 재요청 → 잔액 여전히 400 (또 600 안 빠짐)
        MvcResult secondUse = mockMvc.perform(post("/api/points/use")
                        .header("Idempotency-Key", "use-key")
                        .contentType(MediaType.APPLICATION_JSON).content(useBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", comparesEqualTo(400)))
                .andReturn();
        long secondUseKey = objectMapper.readTree(secondUse.getResponse().getContentAsString())
                .get("transactionId").asLong();

        org.assertj.core.api.Assertions.assertThat(secondUseKey).isEqualTo(firstUseKey);
    }
}
