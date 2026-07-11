package com.miz.point.repository;

import com.miz.point.domain.UserPointAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPointAccountRepository extends JpaRepository<UserPointAccount, String> {

    /**
     * 사용자 계정 행을 비관적 쓰기 락으로 조회한다. 각 변경 작업 시작 시 호출해 사용자
     * 단위로 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserPointAccount a where a.userId = :userId")
    Optional<UserPointAccount> findByIdForUpdate(@Param("userId") String userId);
}
