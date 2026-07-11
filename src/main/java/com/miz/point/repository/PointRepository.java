package com.miz.point.repository;

import com.miz.point.domain.Point;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointRepository extends JpaRepository<Point, Long> {

    Optional<Point> findByTransactionId(Long transactionId);

    /**
     * 사용 후보 포인트를 소진 우선순위대로 조회한다.
     * 명세 3.3.3: 수기지급(MANUAL) 우선 → 만료 임박(expiresAt ASC) 순.
     * 동일 조건은 transactionId ASC로 결정적 정렬.
     */
    @Query("""
            select p from Point p
            where p.userId = :userId
              and p.status = com.miz.point.domain.PointStatus.AVAILABLE
              and p.remainingAmount > 0
              and p.expiresAt > :now
            order by case when p.sourceType = com.miz.point.domain.SourceType.MANUAL then 0 else 1 end asc,
                     p.expiresAt asc,
                     p.transactionId asc
            """)
    List<Point> findUsableOrdered(@Param("userId") String userId, @Param("now") Instant now);

    /**
     * 가용 잔액 = AVAILABLE & 미만료 포인트의 remainingAmount 합.
     */
    @Query("""
            select coalesce(sum(p.remainingAmount), 0) from Point p
            where p.userId = :userId
              and p.status = com.miz.point.domain.PointStatus.AVAILABLE
              and p.expiresAt > :now
            """)
    java.math.BigDecimal sumAvailableBalance(@Param("userId") String userId,
                                             @Param("now") Instant now);
}
