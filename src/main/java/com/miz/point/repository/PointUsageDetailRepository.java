package com.miz.point.repository;

import com.miz.point.domain.PointUsageDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointUsageDetailRepository extends JpaRepository<PointUsageDetail, Long> {

    /**
     * 특정 USE 트랜잭션이 소진한 상세 내역을 소진 순서(id ASC)대로 조회한다.
     * 사용취소 시 이 순서로 복원한다.
     */
    List<PointUsageDetail> findByUseTransactionIdOrderByIdAsc(Long useTransactionId);
}
